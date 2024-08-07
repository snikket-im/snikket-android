package eu.siacs.conversations.xmpp;

import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.XmppDomainVerifier;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.sasl.ChannelBinding;
import eu.siacs.conversations.crypto.sasl.ChannelBindingMechanism;
import eu.siacs.conversations.crypto.sasl.HashedToken;
import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.parser.MessageParser;
import eu.siacs.conversations.parser.PresenceParser;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MemorizingTrustManager;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.SSLSockets;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.bind.Bind2;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;

import im.conversations.android.xmpp.model.AuthenticationFailure;
import im.conversations.android.xmpp.model.AuthenticationRequest;
import im.conversations.android.xmpp.model.AuthenticationStreamFeature;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.bind2.Bind;
import im.conversations.android.xmpp.model.bind2.Bound;
import im.conversations.android.xmpp.model.csi.Active;
import im.conversations.android.xmpp.model.csi.Inactive;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.fast.Fast;
import im.conversations.android.xmpp.model.fast.RequestToken;
import im.conversations.android.xmpp.model.jingle.Jingle;
import im.conversations.android.xmpp.model.sasl.Auth;
import im.conversations.android.xmpp.model.sasl.Failure;
import im.conversations.android.xmpp.model.sasl.Mechanisms;
import im.conversations.android.xmpp.model.sasl.Response;
import im.conversations.android.xmpp.model.sasl.SaslError;
import im.conversations.android.xmpp.model.sasl.Success;
import im.conversations.android.xmpp.model.sasl2.Authenticate;
import im.conversations.android.xmpp.model.sasl2.Authentication;
import im.conversations.android.xmpp.model.sasl2.UserAgent;
import im.conversations.android.xmpp.model.sm.Ack;
import im.conversations.android.xmpp.model.sm.Enable;
import im.conversations.android.xmpp.model.sm.Enabled;
import im.conversations.android.xmpp.model.sm.Failed;
import im.conversations.android.xmpp.model.sm.Request;
import im.conversations.android.xmpp.model.sm.Resume;
import im.conversations.android.xmpp.model.sm.Resumed;
import im.conversations.android.xmpp.model.sm.StreamManagement;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.stanza.Stanza;
import im.conversations.android.xmpp.model.tls.Proceed;
import im.conversations.android.xmpp.model.tls.StartTls;
import im.conversations.android.xmpp.processor.BindProcessor;

import okhttp3.HttpUrl;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

public class XmppConnection implements Runnable {

    protected final Account account;
    private final Features features = new Features(this);
    private final HashMap<Jid, ServiceDiscoveryResult> disco = new HashMap<>();
    private final HashMap<String, Jid> commands = new HashMap<>();
    private final SparseArray<Stanza> mStanzaQueue = new SparseArray<>();
    private final Hashtable<String, Pair<Iq, Consumer<Iq>>> packetCallbacks = new Hashtable<>();
    private final Set<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners =
            new HashSet<>();
    private final AppSettings appSettings;
    private final XmppConnectionService mXmppConnectionService;
    private Socket socket;
    private XmlReader tagReader;
    private TagWriter tagWriter = new TagWriter();
    private boolean shouldAuthenticate = true;
    private boolean inSmacksSession = false;
    private boolean quickStartInProgress = false;
    private boolean isBound = false;
    private boolean offlineMessagesRetrieved = false;
    private im.conversations.android.xmpp.model.streams.Features streamFeatures;
    private im.conversations.android.xmpp.model.streams.Features boundStreamFeatures;
    private StreamId streamId = null;
    private int stanzasReceived = 0;
    private int stanzasSent = 0;
    private int stanzasSentBeforeAuthentication;
    private long lastPacketReceived = 0;
    private long lastPingSent = 0;
    private long lastConnect = 0;
    private long lastSessionStarted = 0;
    private long lastDiscoStarted = 0;
    private boolean isMamPreferenceAlways = false;
    private final AtomicInteger mPendingServiceDiscoveries = new AtomicInteger(0);
    private final AtomicBoolean mWaitForDisco = new AtomicBoolean(true);
    private final AtomicBoolean mWaitingForSmCatchup = new AtomicBoolean(false);
    private final AtomicInteger mSmCatchupMessageCounter = new AtomicInteger(0);
    private boolean mInteractive = false;
    private int attempt = 0;
    private OnJinglePacketReceived jingleListener = null;

    private final Consumer<Presence> presenceListener;
    private final Consumer<Iq> unregisteredIqListener;
    private final Consumer<im.conversations.android.xmpp.model.stanza.Message> messageListener;
    private OnStatusChanged statusListener = null;
    private final Runnable bindListener;
    private OnMessageAcknowledged acknowledgedListener = null;
    private LoginInfo loginInfo;
    private HashedToken.Mechanism hashTokenRequest;
    private HttpUrl redirectionUrl = null;
    private String verifiedHostname = null;
    private Resolver.Result currentResolverResult;
    private Resolver.Result seeOtherHostResolverResult;
    private volatile Thread mThread;
    private CountDownLatch mStreamCountDownLatch;

    public XmppConnection(final Account account, final XmppConnectionService service) {
        this.account = account;
        this.mXmppConnectionService = service;
        this.appSettings = mXmppConnectionService.getAppSettings();
        this.presenceListener = new PresenceParser(service, account);
        this.unregisteredIqListener = new IqParser(service, account);
        this.messageListener = new MessageParser(service, account);
        this.bindListener = new BindProcessor(service, account);
    }

    private static void fixResource(final Context context, final Account account) {
        String resource = account.getResource();
        int fixedPartLength =
                context.getString(R.string.app_name).length() + 1; // include the trailing dot
        int randomPartLength = 4; // 3 bytes
        if (resource != null && resource.length() > fixedPartLength + randomPartLength) {
            if (validBase64(
                    resource.substring(fixedPartLength, fixedPartLength + randomPartLength))) {
                account.setResource(resource.substring(0, fixedPartLength + randomPartLength));
            }
        }
    }

    private static boolean validBase64(String input) {
        try {
            return Base64.decode(input, Base64.URL_SAFE).length == 3;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private void changeStatus(final Account.State nextStatus) {
        synchronized (this) {
            if (Thread.currentThread().isInterrupted()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": not changing status to "
                                + nextStatus
                                + " because thread was interrupted");
                return;
            }
            if (account.getStatus() != nextStatus) {
                if (nextStatus == Account.State.OFFLINE
                        && account.getStatus() != Account.State.CONNECTING
                        && account.getStatus() != Account.State.ONLINE
                        && account.getStatus() != Account.State.DISABLED
                        && account.getStatus() != Account.State.LOGGED_OUT) {
                    return;
                }
                if (nextStatus == Account.State.ONLINE) {
                    this.attempt = 0;
                }
                account.setStatus(nextStatus);
            } else {
                return;
            }
        }
        if (statusListener != null) {
            statusListener.onStatusChanged(account);
        }
    }

    public Jid getJidForCommand(final String node) {
        synchronized (this.commands) {
            return this.commands.get(node);
        }
    }

    public void prepareNewConnection() {
        this.lastConnect = SystemClock.elapsedRealtime();
        this.lastPingSent = SystemClock.elapsedRealtime();
        this.lastDiscoStarted = Long.MAX_VALUE;
        this.mWaitingForSmCatchup.set(false);
        this.changeStatus(Account.State.CONNECTING);
    }

    public boolean isWaitingForSmCatchup() {
        return mWaitingForSmCatchup.get();
    }

    public void incrementSmCatchupMessageCounter() {
        this.mSmCatchupMessageCounter.incrementAndGet();
    }

    protected void connect() {
        if (mXmppConnectionService.areMessagesInitialized()) {
            mXmppConnectionService.resetSendingToWaiting(account);
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": connecting");
        this.loginInfo = null;
        this.features.encryptionEnabled = false;
        this.inSmacksSession = false;
        this.quickStartInProgress = false;
        this.isBound = false;
        this.attempt++;
        this.verifiedHostname =
                null; // will be set if user entered hostname is being used or hostname was verified
        // with dnssec
        try {
            Socket localSocket;
            shouldAuthenticate = !account.isOptionSet(Account.OPTION_REGISTER);
            this.changeStatus(Account.State.CONNECTING);
            final boolean useTor = mXmppConnectionService.useTorToConnect() || account.isOnion();
            final boolean extended = mXmppConnectionService.showExtendedConnectionOptions();
            if (useTor) {
                String destination;
                if (account.getHostname().isEmpty() || account.isOnion()) {
                    destination = account.getServer();
                } else {
                    destination = account.getHostname();
                    this.verifiedHostname = destination;
                }

                final int port = account.getPort();
                final boolean directTls = Resolver.useDirectTls(port);

                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": connect to "
                                + destination
                                + " via Tor. directTls="
                                + directTls);
                localSocket = SocksSocketFactory.createSocketOverTor(destination, port);

                if (directTls) {
                    localSocket = upgradeSocketToTls(localSocket);
                    features.encryptionEnabled = true;
                }

                try {
                    startXmpp(localSocket);
                } catch (final InterruptedException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": thread was interrupted before beginning stream");
                    return;
                } catch (final Exception e) {
                    throw new IOException("Could not start stream", e);
                }
            } else {
                final String domain = account.getServer();
                final List<Resolver.Result> results = new ArrayList<>();
                final boolean hardcoded = extended && !account.getHostname().isEmpty();
                if (hardcoded) {
                    results.addAll(
                            Resolver.fromHardCoded(account.getHostname(), account.getPort()));
                } else {
                    results.addAll(Resolver.resolve(domain));
                }
                if (Thread.currentThread().isInterrupted()) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": Thread was interrupted");
                    return;
                }
                if (results.isEmpty()) {
                    Log.e(
                            Config.LOGTAG,
                            account.getJid().asBareJid() + ": Resolver results were empty");
                    return;
                }
                final Resolver.Result storedBackupResult;
                if (hardcoded) {
                    storedBackupResult = null;
                } else {
                    storedBackupResult =
                            mXmppConnectionService.databaseBackend.findResolverResult(domain);
                    if (storedBackupResult != null && !results.contains(storedBackupResult)) {
                        results.add(storedBackupResult);
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": loaded backup resolver result from db: "
                                        + storedBackupResult);
                    }
                }
                final StreamId streamId = this.streamId;
                final Resolver.Result resumeLocation = streamId == null ? null : streamId.location;
                if (resumeLocation != null) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": injected resume location on position 0");
                    results.add(0, resumeLocation);
                }
                final Resolver.Result seeOtherHost = this.seeOtherHostResolverResult;
                if (seeOtherHost != null) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": injected see-other-host on position 0");
                    results.add(0, seeOtherHost);
                }
                for (final Iterator<Resolver.Result> iterator = results.iterator();
                        iterator.hasNext(); ) {
                    final Resolver.Result result = iterator.next();
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": Thread was interrupted");
                        return;
                    }
                    try {
                        // if tls is true, encryption is implied and must not be started
                        features.encryptionEnabled = result.isDirectTls();
                        verifiedHostname =
                                result.isAuthenticated() ? result.getHostname().toString() : null;
                        final InetSocketAddress addr;
                        if (result.getIp() != null) {
                            addr = new InetSocketAddress(result.getIp(), result.getPort());
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid().toString()
                                            + ": using values from resolver "
                                            + (result.getHostname() == null
                                                    ? ""
                                                    : result.getHostname().toString() + "/")
                                            + result.getIp().getHostAddress()
                                            + ":"
                                            + result.getPort()
                                            + " tls: "
                                            + features.encryptionEnabled);
                        } else {
                            addr =
                                    new InetSocketAddress(
                                            IDN.toASCII(result.getHostname().toString()),
                                            result.getPort());
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid().toString()
                                            + ": using values from resolver "
                                            + result.getHostname().toString()
                                            + ":"
                                            + result.getPort()
                                            + " tls: "
                                            + features.encryptionEnabled);
                        }

                        localSocket = new Socket();
                        localSocket.connect(addr, Config.SOCKET_TIMEOUT * 1000);

                        if (features.encryptionEnabled) {
                            localSocket = upgradeSocketToTls(localSocket);
                        }

                        localSocket.setSoTimeout(Config.SOCKET_TIMEOUT * 1000);
                        if (startXmpp(localSocket)) {
                            localSocket.setSoTimeout(
                                    0); // reset to 0; once the connection is established we don’t
                            // want this
                            if (!hardcoded && !result.equals(storedBackupResult)) {
                                mXmppConnectionService.databaseBackend.saveResolverResult(
                                        domain, result);
                            }
                            this.currentResolverResult = result;
                            this.seeOtherHostResolverResult = null;
                            break; // successfully connected to server that speaks xmpp
                        } else {
                            FileBackend.close(localSocket);
                            throw new StateChangingException(Account.State.STREAM_OPENING_ERROR);
                        }
                    } catch (final StateChangingException e) {
                        if (!iterator.hasNext()) {
                            throw e;
                        }
                    } catch (InterruptedException e) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": thread was interrupted before beginning stream");
                        return;
                    } catch (final Throwable e) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid().toString()
                                        + ": "
                                        + e.getMessage()
                                        + "("
                                        + e.getClass().getName()
                                        + ")");
                        if (!iterator.hasNext()) {
                            throw new UnknownHostException();
                        }
                    }
                }
            }
            processStream();
        } catch (final SecurityException e) {
            this.changeStatus(Account.State.MISSING_INTERNET_PERMISSION);
        } catch (final StateChangingException e) {
            this.changeStatus(e.state);
        } catch (final UnknownHostException
                | ConnectException
                | SocksSocketFactory.HostNotFoundException e) {
            this.changeStatus(Account.State.SERVER_NOT_FOUND);
        } catch (final SocksSocketFactory.SocksProxyNotFoundException e) {
            this.changeStatus(Account.State.TOR_NOT_AVAILABLE);
        } catch (final IOException | XmlPullParserException e) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid().toString() + ": " + e.getMessage());
            this.changeStatus(Account.State.OFFLINE);
            this.attempt = Math.max(0, this.attempt - 1);
        } finally {
            if (!Thread.currentThread().isInterrupted()) {
                forceCloseSocket();
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": not force closing socket because thread was interrupted");
            }
        }
    }

    /**
     * Starts xmpp protocol, call after connecting to socket
     *
     * @return true if server returns with valid xmpp, false otherwise
     */
    private boolean startXmpp(final Socket socket) throws Exception {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        this.socket = socket;
        tagReader = new XmlReader();
        if (tagWriter != null) {
            tagWriter.forceClose();
        }
        tagWriter = new TagWriter();
        tagWriter.setOutputStream(socket.getOutputStream());
        tagReader.setInputStream(socket.getInputStream());
        tagWriter.beginDocument();
        final boolean quickStart;
        if (socket instanceof SSLSocket sslSocket) {
            SSLSockets.log(account, sslSocket);
            quickStart = establishStream(SSLSockets.version(sslSocket));
        } else {
            quickStart = establishStream(SSLSockets.Version.NONE);
        }
        final Tag tag = tagReader.readTag();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
        if (tag == null) {
            return false;
        }
        final boolean success = tag.isStart("stream", Namespace.STREAMS);
        if (success) {
            final var from = tag.getAttribute("from");
            if (from == null || !from.equals(account.getServer())) {
                throw new StateChangingException(Account.State.HOST_UNKNOWN);
            }
        }
        if (success && quickStart) {
            this.quickStartInProgress = true;
        }
        return success;
    }

    private SSLSocketFactory getSSLSocketFactory()
            throws NoSuchAlgorithmException, KeyManagementException {
        final SSLContext sc = SSLSockets.getSSLContext();
        final MemorizingTrustManager trustManager =
                this.mXmppConnectionService.getMemorizingTrustManager();
        final KeyManager[] keyManager;
        if (account.getPrivateKeyAlias() != null) {
            keyManager = new KeyManager[] {new MyKeyManager()};
        } else {
            keyManager = null;
        }
        final String domain = account.getServer();
        sc.init(
                keyManager,
                new X509TrustManager[] {
                    mInteractive
                            ? trustManager.getInteractive(domain)
                            : trustManager.getNonInteractive(domain)
                },
                SECURE_RANDOM);
        return sc.getSocketFactory();
    }

    @Override
    public void run() {
        synchronized (this) {
            this.mThread = Thread.currentThread();
            if (this.mThread.isInterrupted()) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": aborting connect because thread was interrupted");
                return;
            }
            forceCloseSocket();
        }
        connect();
    }

    private void processStream() throws XmlPullParserException, IOException {
        final CountDownLatch streamCountDownLatch = new CountDownLatch(1);
        this.mStreamCountDownLatch = streamCountDownLatch;
        Tag nextTag = tagReader.readTag();
        while (nextTag != null && !nextTag.isEnd("stream")) {
            if (nextTag.isStart("error")) {
                processStreamError(nextTag);
            } else if (nextTag.isStart("features", Namespace.STREAMS)) {
                processStreamFeatures(nextTag);
            } else if (nextTag.isStart("proceed", Namespace.TLS)) {
                switchOverToTls(nextTag);
            } else if (nextTag.isStart("failure", Namespace.TLS)) {
                throw new StateChangingException(Account.State.TLS_ERROR);
            } else if (account.isOptionSet(Account.OPTION_REGISTER)
                    && nextTag.isStart("iq", Namespace.JABBER_CLIENT)) {
                processIq(nextTag);
            } else if (!isSecure() || this.loginInfo == null) {
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            } else if (nextTag.isStart("success")) {
                final Element success = tagReader.readElement(nextTag);
                if (processSuccess(success)) {
                    break;
                }
            } else if (nextTag.isStart("failure", Namespace.SASL)) {
                final var failure = tagReader.readElement(nextTag, Failure.class);
                processFailure(failure);
            } else if (nextTag.isStart("failure", Namespace.SASL_2)) {
                final var failure =
                        tagReader.readElement(
                                nextTag, im.conversations.android.xmpp.model.sasl2.Failure.class);
                processFailure(failure);
            } else if (nextTag.isStart("continue", Namespace.SASL_2)) {
                // two step sasl2 - we don’t support this yet
                throw new StateChangingException(Account.State.INCOMPATIBLE_CLIENT);
            } else if (nextTag.isStart("challenge")) {
                final Element challenge = tagReader.readElement(nextTag);
                processChallenge(challenge);
            } else if (!LoginInfo.isSuccess(this.loginInfo)) {
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            } else if (this.streamId != null
                    && nextTag.isStart("resumed", Namespace.STREAM_MANAGEMENT)) {
                final Resumed resumed = tagReader.readElement(nextTag, Resumed.class);
                processResumed(resumed);
            } else if (nextTag.isStart("failed", Namespace.STREAM_MANAGEMENT)) {
                final Failed failed = tagReader.readElement(nextTag, Failed.class);
                processFailed(failed, true);
            } else if (nextTag.isStart("iq", Namespace.JABBER_CLIENT)) {
                processIq(nextTag);
            } else if (!isBound) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": server sent unexpected"
                                + nextTag.identifier());
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            } else if (nextTag.isStart("message", Namespace.JABBER_CLIENT)) {
                processMessage(nextTag);
            } else if (nextTag.isStart("presence", Namespace.JABBER_CLIENT)) {
                processPresence(nextTag);
            } else if (nextTag.isStart("enabled", Namespace.STREAM_MANAGEMENT)) {
                final var enabled = tagReader.readElement(nextTag, Enabled.class);
                processEnabled(enabled);
            } else if (nextTag.isStart("r", Namespace.STREAM_MANAGEMENT)) {
                tagReader.readElement(nextTag);
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": acknowledging stanza #"
                                    + this.stanzasReceived);
                }
                final Ack ack = new Ack(this.stanzasReceived);
                tagWriter.writeStanzaAsync(ack);
            } else if (nextTag.isStart("a", Namespace.STREAM_MANAGEMENT)) {
                boolean accountUiNeedsRefresh = false;
                synchronized (NotificationService.CATCHUP_LOCK) {
                    if (mWaitingForSmCatchup.compareAndSet(true, false)) {
                        final int messageCount = mSmCatchupMessageCounter.get();
                        final int pendingIQs = packetCallbacks.size();
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": SM catchup complete (messages="
                                        + messageCount
                                        + ", pending IQs="
                                        + pendingIQs
                                        + ")");
                        accountUiNeedsRefresh = true;
                        if (messageCount > 0) {
                            mXmppConnectionService
                                    .getNotificationService()
                                    .finishBacklog(true, account);
                        }
                    }
                }
                if (accountUiNeedsRefresh) {
                    mXmppConnectionService.updateAccountUi();
                }
                final var ack = tagReader.readElement(nextTag, Ack.class);
                lastPacketReceived = SystemClock.elapsedRealtime();
                final boolean acknowledgedMessages;
                synchronized (this.mStanzaQueue) {
                    final Optional<Integer> serverSequence = ack.getHandled();
                    if (serverSequence.isPresent()) {
                        acknowledgedMessages = acknowledgeStanzaUpTo(serverSequence.get());
                    } else {
                        acknowledgedMessages = false;
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": server send ack without sequence number");
                    }
                }
                if (acknowledgedMessages) {
                    mXmppConnectionService.updateConversationUi();
                }
            } else {
                Log.e(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": Encountered unknown stream element"
                                + nextTag.identifier());
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            }
            nextTag = tagReader.readTag();
        }
        if (nextTag != null && nextTag.isEnd("stream")) {
            streamCountDownLatch.countDown();
        }
    }

    private void processChallenge(final Element challenge) throws IOException {
        final SaslMechanism.Version version;
        try {
            version = SaslMechanism.Version.of(challenge);
        } catch (final IllegalArgumentException e) {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        final StreamElement response;
        if (version == SaslMechanism.Version.SASL) {
            response = new Response();
        } else if (version == SaslMechanism.Version.SASL_2) {
            response = new im.conversations.android.xmpp.model.sasl2.Response();
        } else {
            throw new AssertionError("Missing implementation for " + version);
        }
        final LoginInfo currentLoginInfo = this.loginInfo;
        if (currentLoginInfo == null || LoginInfo.isSuccess(currentLoginInfo)) {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        try {
            response.setContent(
                    currentLoginInfo.saslMechanism.getResponse(
                            challenge.getContent(), sslSocketOrNull(socket)));
        } catch (final SaslMechanism.AuthenticationException e) {
            // TODO: Send auth abort tag.
            Log.e(Config.LOGTAG, e.toString());
            throw new StateChangingException(Account.State.UNAUTHORIZED);
        }
        tagWriter.writeElement(response);
    }

    private boolean processSuccess(final Element element)
            throws IOException, XmlPullParserException {
        final LoginInfo currentLoginInfo = this.loginInfo;
        final SaslMechanism currentSaslMechanism = LoginInfo.mechanism(currentLoginInfo);
        if (currentLoginInfo == null || currentSaslMechanism == null) {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        final SaslMechanism.Version version;
        final String challenge;
        if (element instanceof Success success) {
            challenge = success.getContent();
            version = SaslMechanism.Version.SASL;
        } else if (element instanceof im.conversations.android.xmpp.model.sasl2.Success success) {
            challenge = success.findChildContent("additional-data");
            version = SaslMechanism.Version.SASL_2;
        } else {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        try {
            currentLoginInfo.success(challenge, sslSocketOrNull(socket));
        } catch (final SaslMechanism.AuthenticationException e) {
            Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": authentication failure ", e);
            throw new StateChangingException(Account.State.UNAUTHORIZED);
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid().toString() + ": logged in (using " + version + ")");
        if (SaslMechanism.pin(currentSaslMechanism)) {
            account.setPinnedMechanism(currentSaslMechanism);
        }
        if (element instanceof im.conversations.android.xmpp.model.sasl2.Success success) {
            final var authorizationJid = success.getAuthorizationIdentifier();
            checkAssignedDomainOrThrow(authorizationJid);
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": SASL 2.0 authorization identifier was "
                            + authorizationJid);
            // TODO this should only happen when we used Bind 2
            if (authorizationJid.isFullJid() && account.setJid(authorizationJid)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": jid changed during SASL 2.0. updating database");
            }
            final Bound bound = success.getExtension(Bound.class);
            final Resumed resumed = success.getExtension(Resumed.class);
            final Failed failed = success.getExtension(Failed.class);
            final Element tokenWrapper = success.findChild("token", Namespace.FAST);
            final String token = tokenWrapper == null ? null : tokenWrapper.getAttribute("token");
            if (bound != null && resumed != null) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": server sent bound and resumed in SASL2 success");
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            }
            if (resumed != null && streamId != null) {
                if (this.boundStreamFeatures != null) {
                    this.streamFeatures = this.boundStreamFeatures;
                    Log.d(
                            Config.LOGTAG,
                            "putting previous stream features back in place: "
                                    + XmlHelper.printElementNames(this.boundStreamFeatures));
                }
                processResumed(resumed);
            } else if (failed != null) {
                processFailed(failed, false); // wait for new stream features
            }
            if (bound != null) {
                clearIqCallbacks();
                this.isBound = true;
                processNopStreamFeatures();
                this.boundStreamFeatures = this.streamFeatures;
                final Enabled streamManagementEnabled = bound.getExtension(Enabled.class);
                final Element carbonsEnabled = bound.findChild("enabled", Namespace.CARBONS);
                final boolean waitForDisco;
                if (streamManagementEnabled != null) {
                    resetOutboundStanzaQueue();
                    processEnabled(streamManagementEnabled);
                    waitForDisco = true;
                } else {
                    // if we did not enable stream management in bind do it now
                    waitForDisco = enableStreamManagement();
                }
                final boolean negotiatedCarbons;
                if (carbonsEnabled != null) {
                    negotiatedCarbons = true;
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": successfully enabled carbons (via Bind 2.0)");
                    features.carbonsEnabled = true;
                } else if (currentLoginInfo.inlineBindFeatures != null
                        && currentLoginInfo.inlineBindFeatures.contains(Namespace.CARBONS)) {
                    negotiatedCarbons = true;
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": successfully enabled carbons (via Bind 2.0/implicit)");
                    features.carbonsEnabled = true;
                } else {
                    negotiatedCarbons = false;
                }
                sendPostBindInitialization(waitForDisco, negotiatedCarbons);
            }
            final HashedToken.Mechanism tokenMechanism;
            if (SaslMechanism.hashedToken(currentSaslMechanism)) {
                tokenMechanism = ((HashedToken) currentSaslMechanism).getTokenMechanism();
            } else if (this.hashTokenRequest != null) {
                tokenMechanism = this.hashTokenRequest;
            } else {
                tokenMechanism = null;
            }
            if (tokenMechanism != null && !Strings.isNullOrEmpty(token)) {
                if (ChannelBinding.priority(tokenMechanism.channelBinding)
                        >= ChannelBindingMechanism.getPriority(currentSaslMechanism)) {
                    this.account.setFastToken(tokenMechanism, token);
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": storing hashed token "
                                    + tokenMechanism);
                } else {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": not accepting hashed token "
                                    + tokenMechanism.name()
                                    + " for log in mechanism "
                                    + currentSaslMechanism.getMechanism());
                    this.account.resetFastToken();
                }
            } else if (this.hashTokenRequest != null) {
                Log.w(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": no response to our hashed token request "
                                + this.hashTokenRequest);
            }
        }
        mXmppConnectionService.databaseBackend.updateAccount(account);
        this.quickStartInProgress = false;
        if (version == SaslMechanism.Version.SASL) {
            tagReader.reset();
            sendStartStream(false, true);
            final Tag tag = tagReader.readTag();
            if (tag != null && tag.isStart("stream", Namespace.STREAMS)) {
                processStream();
                return true;
            } else {
                throw new StateChangingException(Account.State.STREAM_OPENING_ERROR);
            }
        } else {
            return false;
        }
    }

    private void resetOutboundStanzaQueue() {
        synchronized (this.mStanzaQueue) {
            final ImmutableList.Builder<Stanza> intermediateStanzasBuilder =
                    new ImmutableList.Builder<>();
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": stanzas sent before auth: "
                                + this.stanzasSentBeforeAuthentication);
            }
            for (int i = this.stanzasSentBeforeAuthentication + 1; i <= this.stanzasSent; ++i) {
                final Stanza stanza = this.mStanzaQueue.get(i);
                if (stanza != null) {
                    intermediateStanzasBuilder.add(stanza);
                }
            }
            this.mStanzaQueue.clear();
            final var intermediateStanzas = intermediateStanzasBuilder.build();
            for (int i = 0; i < intermediateStanzas.size(); ++i) {
                this.mStanzaQueue.append(i + 1, intermediateStanzas.get(i));
            }
            this.stanzasSent = intermediateStanzas.size();
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": resetting outbound stanza queue to "
                                + this.stanzasSent);
            }
        }
    }

    private void processNopStreamFeatures() throws IOException {
        final Tag tag = tagReader.readTag();
        if (tag != null && tag.isStart("features", Namespace.STREAMS)) {
            this.streamFeatures =
                    tagReader.readElement(
                            tag, im.conversations.android.xmpp.model.streams.Features.class);
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": processed NOP stream features after success: "
                            + XmlHelper.printElementNames(this.streamFeatures));
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received " + tag);
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": server did not send stream features after SASL2 success");
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
    }

    private void processFailure(final AuthenticationFailure failure) throws IOException {
        final SaslMechanism.Version version;
        try {
            version = SaslMechanism.Version.of(failure);
        } catch (final IllegalArgumentException e) {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        Log.d(Config.LOGTAG, failure.toString());
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": login failure " + version);
        if (SaslMechanism.hashedToken(LoginInfo.mechanism(this.loginInfo))) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": resetting token");
            account.resetFastToken();
            mXmppConnectionService.databaseBackend.updateAccount(account);
        }
        final var errorCondition = failure.getErrorCondition();
        if (errorCondition instanceof SaslError.InvalidMechanism
                || errorCondition instanceof SaslError.MechanismTooWeak) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": invalid or too weak mechanism. resetting quick start");
            if (account.setOption(Account.OPTION_QUICKSTART_AVAILABLE, false)) {
                mXmppConnectionService.databaseBackend.updateAccount(account);
            }
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        } else if (errorCondition instanceof SaslError.TemporaryAuthFailure) {
            throw new StateChangingException(Account.State.TEMPORARY_AUTH_FAILURE);
        } else if (errorCondition instanceof SaslError.AccountDisabled) {
            final String text = failure.getText();
            if (Strings.isNullOrEmpty(text)) {
                throw new StateChangingException(Account.State.UNAUTHORIZED);
            }
            final Matcher matcher = Patterns.AUTOLINK_WEB_URL.matcher(text);
            if (matcher.find()) {
                final HttpUrl url;
                try {
                    url = HttpUrl.get(text.substring(matcher.start(), matcher.end()));
                } catch (final IllegalArgumentException e) {
                    throw new StateChangingException(Account.State.UNAUTHORIZED);
                }
                if (url.isHttps()) {
                    this.redirectionUrl = url;
                    throw new StateChangingException(Account.State.PAYMENT_REQUIRED);
                }
            }
        }
        if (SaslMechanism.hashedToken(LoginInfo.mechanism(this.loginInfo))) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": fast authentication failed. falling back to regular authentication");
            authenticate();
        } else {
            throw new StateChangingException(Account.State.UNAUTHORIZED);
        }
    }

    private static SSLSocket sslSocketOrNull(final Socket socket) {
        if (socket instanceof SSLSocket) {
            return (SSLSocket) socket;
        } else {
            return null;
        }
    }

    private void processEnabled(final Enabled enabled) {
        final StreamId streamId = getStreamId(enabled);
        if (streamId == null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": stream management enabled");
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": stream management enabled. resume at: "
                            + streamId.location);
        }
        this.streamId = streamId;
        this.stanzasReceived = 0;
        this.inSmacksSession = true;
        final var r = new Request();
        tagWriter.writeStanzaAsync(r);
    }

    @Nullable
    private StreamId getStreamId(final Enabled enabled) {
        final Optional<String> id = enabled.getResumeId();
        final String locationAttribute = enabled.getLocation();
        final Resolver.Result currentResolverResult = this.currentResolverResult;
        final Resolver.Result location;
        if (Strings.isNullOrEmpty(locationAttribute) || currentResolverResult == null) {
            location = null;
        } else {
            location = currentResolverResult.seeOtherHost(locationAttribute);
        }
        return id.isPresent() ? new StreamId(id.get(), location) : null;
    }

    private void processResumed(final Resumed resumed) throws StateChangingException {
        this.inSmacksSession = true;
        this.isBound = true;
        this.tagWriter.writeStanzaAsync(new Request());
        lastPacketReceived = SystemClock.elapsedRealtime();
        final Optional<Integer> h = resumed.getHandled();
        final int serverCount;
        if (h.isPresent()) {
            serverCount = h.get();
        } else {
            resetStreamId();
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        final ArrayList<Stanza> failedStanzas = new ArrayList<>();
        final boolean acknowledgedMessages;
        synchronized (this.mStanzaQueue) {
            if (serverCount < stanzasSent) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid() + ": session resumed with lost packages");
                stanzasSent = serverCount;
            } else {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": session resumed");
            }
            acknowledgedMessages = acknowledgeStanzaUpTo(serverCount);
            for (int i = 0; i < this.mStanzaQueue.size(); ++i) {
                failedStanzas.add(mStanzaQueue.valueAt(i));
            }
            mStanzaQueue.clear();
        }
        if (acknowledgedMessages) {
            mXmppConnectionService.updateConversationUi();
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": resending " + failedStanzas.size() + " stanzas");
        for (final Stanza packet : failedStanzas) {
            if (packet instanceof im.conversations.android.xmpp.model.stanza.Message message) {
                mXmppConnectionService.markMessage(
                        account,
                        message.getTo().asBareJid(),
                        message.getId(),
                        Message.STATUS_UNSEND);
            }
            sendPacket(packet);
        }
        changeStatusToOnline();
    }

    private void changeStatusToOnline() {
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": online with resource " + account.getResource());
        changeStatus(Account.State.ONLINE);
    }

    private void processFailed(final Failed failed, final boolean sendBindRequest) {
        final Optional<Integer> serverCount = failed.getHandled();
        if (serverCount.isPresent()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": resumption failed but server acknowledged stanza #"
                            + serverCount.get());
            final boolean acknowledgedMessages;
            synchronized (this.mStanzaQueue) {
                acknowledgedMessages = acknowledgeStanzaUpTo(serverCount.get());
            }
            if (acknowledgedMessages) {
                mXmppConnectionService.updateConversationUi();
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": resumption failed ("
                            + XmlHelper.print(failed.getChildren())
                            + ")");
        }
        resetStreamId();
        if (sendBindRequest) {
            sendBindRequest();
        }
    }

    private boolean acknowledgeStanzaUpTo(final int serverCount) {
        if (serverCount > stanzasSent) {
            Log.e(
                    Config.LOGTAG,
                    "server acknowledged more stanzas than we sent. serverCount="
                            + serverCount
                            + ", ourCount="
                            + stanzasSent);
        }
        boolean acknowledgedMessages = false;
        for (int i = 0; i < mStanzaQueue.size(); ++i) {
            if (serverCount >= mStanzaQueue.keyAt(i)) {
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": server acknowledged stanza #"
                                    + mStanzaQueue.keyAt(i));
                }
                final Stanza stanza = mStanzaQueue.valueAt(i);
                if (stanza instanceof im.conversations.android.xmpp.model.stanza.Message packet
                        && acknowledgedListener != null) {
                    final String id = packet.getId();
                    final Jid to = packet.getTo();
                    if (id != null && to != null) {
                        acknowledgedMessages |=
                                acknowledgedListener.onMessageAcknowledged(account, to, id);
                    }
                }
                mStanzaQueue.removeAt(i);
                i--;
            }
        }
        return acknowledgedMessages;
    }

    private <S extends Stanza> @NonNull S processPacket(final Tag currentTag, final Class<S> clazz)
            throws IOException {
        final S stanza = tagReader.readElement(currentTag, clazz);
        if (stanzasReceived == Integer.MAX_VALUE) {
            resetStreamId();
            throw new IOException("time to restart the session. cant handle >2 billion pcks");
        }
        if (inSmacksSession) {
            ++stanzasReceived;
        } else if (features.sm()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": not counting stanza("
                            + stanza.getClass().getSimpleName()
                            + "). Not in smacks session.");
        }
        lastPacketReceived = SystemClock.elapsedRealtime();
        if (Config.BACKGROUND_STANZA_LOGGING && mXmppConnectionService.checkListeners()) {
            Log.d(Config.LOGTAG, "[background stanza] " + stanza);
        }
        return stanza;
    }

    private void processIq(final Tag currentTag) throws IOException {
        final Iq packet = processPacket(currentTag, Iq.class);
        if (packet.isInvalid()) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid iq from='"
                            + packet.getFrom()
                            + "' to='"
                            + packet.getTo()
                            + "'");
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + "Not processing iq. Thread was interrupted");
            return;
        }
        if (packet.hasExtension(Jingle.class) && packet.getType() == Iq.Type.SET && isBound) {
            if (this.jingleListener != null) {
                this.jingleListener.onJinglePacketReceived(account, packet);
            }
        } else {
            final var callback = getIqPacketReceivedCallback(packet);
            if (callback == null) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid().toString()
                                + ": no callback registered for IQ from "
                                + packet.getFrom());
                return;
            }
            try {
                callback.accept(packet);
            } catch (final StateChangingError error) {
                throw new StateChangingException(error.state);
            }
        }
    }

    private Consumer<Iq> getIqPacketReceivedCallback(final Iq stanza)
            throws StateChangingException {
        final boolean isRequest =
                stanza.getType() == Iq.Type.GET || stanza.getType() == Iq.Type.SET;
        if (isRequest) {
            if (isBound) {
                return this.unregisteredIqListener;
            } else {
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            }
        } else {
            synchronized (this.packetCallbacks) {
                final var pair = packetCallbacks.get(stanza.getId());
                if (pair == null) {
                    return null;
                }
                if (pair.first.toServer(account)) {
                    if (stanza.fromServer(account)) {
                        packetCallbacks.remove(stanza.getId());
                        return pair.second;
                    } else {
                        Log.e(
                                Config.LOGTAG,
                                account.getJid().asBareJid().toString()
                                        + ": ignoring spoofed iq packet");
                    }
                } else {
                    if (stanza.getFrom() != null && stanza.getFrom().equals(pair.first.getTo())) {
                        packetCallbacks.remove(stanza.getId());
                        return pair.second;
                    } else {
                        Log.e(
                                Config.LOGTAG,
                                account.getJid().asBareJid().toString()
                                        + ": ignoring spoofed iq packet");
                    }
                }
            }
        }
        return null;
    }

    private void processMessage(final Tag currentTag) throws IOException {
        final var packet =
                processPacket(currentTag, im.conversations.android.xmpp.model.stanza.Message.class);
        if (packet.isInvalid()) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid message from='"
                            + packet.getFrom()
                            + "' to='"
                            + packet.getTo()
                            + "'");
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + "Not processing message. Thread was interrupted");
            return;
        }
        this.messageListener.accept(packet);
    }

    private void processPresence(final Tag currentTag) throws IOException {
        final var packet = processPacket(currentTag, Presence.class);
        if (packet.isInvalid()) {
            Log.e(
                    Config.LOGTAG,
                    "encountered invalid presence from='"
                            + packet.getFrom()
                            + "' to='"
                            + packet.getTo()
                            + "'");
            return;
        }
        if (Thread.currentThread().isInterrupted()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + "Not processing presence. Thread was interrupted");
            return;
        }
        this.presenceListener.accept(packet);
    }

    private void sendStartTLS() throws IOException {
        tagWriter.writeElement(new StartTls());
    }

    private void switchOverToTls(final Tag currentTag) throws XmlPullParserException, IOException {
        tagReader.readElement(currentTag, Proceed.class);
        final Socket socket = this.socket;
        final SSLSocket sslSocket = upgradeSocketToTls(socket);
        this.socket = sslSocket;
        this.tagReader.setInputStream(sslSocket.getInputStream());
        this.tagWriter.setOutputStream(sslSocket.getOutputStream());
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": TLS connection established");
        final boolean quickStart;
        try {
            quickStart = establishStream(SSLSockets.version(sslSocket));
        } catch (final InterruptedException e) {
            return;
        }
        if (quickStart) {
            this.quickStartInProgress = true;
        }
        features.encryptionEnabled = true;
        final Tag tag = tagReader.readTag();
        if (tag != null && tag.isStart("stream", Namespace.STREAMS)) {
            SSLSockets.log(account, sslSocket);
            processStream();
        } else {
            throw new StateChangingException(Account.State.STREAM_OPENING_ERROR);
        }
        sslSocket.close();
    }

    private SSLSocket upgradeSocketToTls(final Socket socket) throws IOException {
        final SSLSocketFactory sslSocketFactory;
        try {
            sslSocketFactory = getSSLSocketFactory();
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw new StateChangingException(Account.State.TLS_ERROR);
        }
        final InetAddress address = socket.getInetAddress();
        final SSLSocket sslSocket =
                (SSLSocket)
                        sslSocketFactory.createSocket(
                                socket, address.getHostAddress(), socket.getPort(), true);
        SSLSockets.setSecurity(sslSocket);
        SSLSockets.setHostname(sslSocket, IDN.toASCII(account.getServer()));
        SSLSockets.setApplicationProtocol(sslSocket, "xmpp-client");
        final XmppDomainVerifier xmppDomainVerifier = new XmppDomainVerifier();
        try {
            if (!xmppDomainVerifier.verify(
                    account.getServer(), this.verifiedHostname, sslSocket.getSession())) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": TLS certificate domain verification failed");
                FileBackend.close(sslSocket);
                throw new StateChangingException(Account.State.TLS_ERROR_DOMAIN);
            }
        } catch (final SSLPeerUnverifiedException e) {
            FileBackend.close(sslSocket);
            throw new StateChangingException(Account.State.TLS_ERROR);
        }
        return sslSocket;
    }

    private void processStreamFeatures(final Tag currentTag) throws IOException {
        this.streamFeatures =
                tagReader.readElement(
                        currentTag, im.conversations.android.xmpp.model.streams.Features.class);
        final boolean isSecure = isSecure();
        final boolean needsBinding = !isBound && !account.isOptionSet(Account.OPTION_REGISTER);
        if (this.quickStartInProgress) {
            if (this.streamFeatures.hasStreamFeature(Authentication.class)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": quick start in progress. ignoring features: "
                                + XmlHelper.printElementNames(this.streamFeatures));
                if (SaslMechanism.hashedToken(LoginInfo.mechanism(this.loginInfo))) {
                    return;
                }
                if (isFastTokenAvailable(this.streamFeatures.getExtension(Authentication.class))) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": fast token available; resetting quick start");
                    account.setOption(Account.OPTION_QUICKSTART_AVAILABLE, false);
                    mXmppConnectionService.databaseBackend.updateAccount(account);
                }
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": server lost support for SASL 2. quick start not possible");
            this.account.setOption(Account.OPTION_QUICKSTART_AVAILABLE, false);
            mXmppConnectionService.databaseBackend.updateAccount(account);
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        if (this.streamFeatures.hasExtension(StartTls.class) && !features.encryptionEnabled) {
            sendStartTLS();
        } else if (this.streamFeatures.hasChild("register", Namespace.REGISTER_STREAM_FEATURE)
                && account.isOptionSet(Account.OPTION_REGISTER)) {
            if (isSecure) {
                register();
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": unable to find STARTTLS for registration process "
                                + XmlHelper.printElementNames(this.streamFeatures));
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            }
        } else if (!this.streamFeatures.hasChild("register", Namespace.REGISTER_STREAM_FEATURE)
                && account.isOptionSet(Account.OPTION_REGISTER)) {
            throw new StateChangingException(Account.State.REGISTRATION_NOT_SUPPORTED);
        } else if (this.streamFeatures.hasStreamFeature(Authentication.class)
                && shouldAuthenticate
                && isSecure) {
            authenticate(SaslMechanism.Version.SASL_2);
        } else if (this.streamFeatures.hasStreamFeature(Mechanisms.class)
                && shouldAuthenticate
                && isSecure) {
            authenticate(SaslMechanism.Version.SASL);
        } else if (this.streamFeatures.streamManagement()
                && isSecure
                && LoginInfo.isSuccess(loginInfo)
                && streamId != null
                && !inSmacksSession) {
            if (Config.EXTENDED_SM_LOGGING) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": resuming after stanza #"
                                + stanzasReceived);
            }
            final var resume = new Resume(this.streamId.id, stanzasReceived);
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            this.tagWriter.writeStanzaAsync(resume);
        } else if (needsBinding) {
            if (this.streamFeatures.hasChild("bind", Namespace.BIND)
                    && isSecure
                    && LoginInfo.isSuccess(loginInfo)) {
                sendBindRequest();
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": unable to find bind feature "
                                + XmlHelper.printElementNames(this.streamFeatures));
                throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
            }
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": received NOP stream features: "
                            + XmlHelper.printElementNames(this.streamFeatures));
        }
    }

    private void authenticate() throws IOException {
        final boolean isSecure = isSecure();
        if (isSecure && this.streamFeatures.hasStreamFeature(Authentication.class)) {
            authenticate(SaslMechanism.Version.SASL_2);
        } else if (isSecure && this.streamFeatures.hasStreamFeature(Mechanisms.class)) {
            authenticate(SaslMechanism.Version.SASL);
        } else {
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
    }

    private boolean isSecure() {
        return features.encryptionEnabled || Config.ALLOW_NON_TLS_CONNECTIONS || account.isOnion();
    }

    private void authenticate(final SaslMechanism.Version version) throws IOException {
        final AuthenticationStreamFeature authElement;
        if (version == SaslMechanism.Version.SASL) {
            authElement = this.streamFeatures.getExtension(Mechanisms.class);
        } else {
            authElement = this.streamFeatures.getExtension(Authentication.class);
        }
        final Collection<String> mechanisms = authElement.getMechanismNames();
        final Element cbElement =
                this.streamFeatures.findChild("sasl-channel-binding", Namespace.CHANNEL_BINDING);
        final Collection<ChannelBinding> channelBindings = ChannelBinding.of(cbElement);
        final SaslMechanism.Factory factory = new SaslMechanism.Factory(account);
        final SaslMechanism saslMechanism =
                factory.of(mechanisms, channelBindings, version, SSLSockets.version(this.socket));
        this.validate(saslMechanism, mechanisms);
        final boolean quickStartAvailable;
        final String firstMessage =
                saslMechanism.getClientFirstMessage(sslSocketOrNull(this.socket));
        final boolean usingFast = SaslMechanism.hashedToken(saslMechanism);
        final AuthenticationRequest authenticate;
        final LoginInfo loginInfo;
        if (version == SaslMechanism.Version.SASL) {
            authenticate = new Auth();
            if (!Strings.isNullOrEmpty(firstMessage)) {
                authenticate.setContent(firstMessage);
            }
            quickStartAvailable = false;
            loginInfo = new LoginInfo(saslMechanism, version, Collections.emptyList());
        } else if (version == SaslMechanism.Version.SASL_2) {
            final Authentication authentication = (Authentication) authElement;
            final var inline = authentication.getInline();
            final boolean sm = inline != null && inline.hasExtension(StreamManagement.class);
            final HashedToken.Mechanism hashTokenRequest;
            if (usingFast) {
                hashTokenRequest = null;
            } else if (inline != null) {
                hashTokenRequest =
                        HashedToken.Mechanism.best(
                                inline.getFastMechanisms(), SSLSockets.version(this.socket));
            } else {
                hashTokenRequest = null;
            }
            final Collection<String> bindFeatures = Bind2.features(inline);
            quickStartAvailable =
                    sm
                            && bindFeatures != null
                            && bindFeatures.containsAll(Bind2.QUICKSTART_FEATURES);
            if (bindFeatures != null) {
                try {
                    mXmppConnectionService.restoredFromDatabaseLatch.await();
                } catch (final InterruptedException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": interrupted while waiting for DB restore during SASL2 bind");
                    return;
                }
            }
            loginInfo = new LoginInfo(saslMechanism, version, bindFeatures);
            this.hashTokenRequest = hashTokenRequest;
            authenticate =
                    generateAuthenticationRequest(
                            firstMessage, usingFast, hashTokenRequest, bindFeatures, sm);
        } else {
            throw new AssertionError("Missing implementation for " + version);
        }
        this.loginInfo = loginInfo;
        if (account.setOption(Account.OPTION_QUICKSTART_AVAILABLE, quickStartAvailable)) {
            mXmppConnectionService.databaseBackend.updateAccount(account);
        }

        Log.d(
                Config.LOGTAG,
                account.getJid().toString()
                        + ": Authenticating with "
                        + version
                        + "/"
                        + LoginInfo.mechanism(loginInfo).getMechanism());
        authenticate.setMechanism(LoginInfo.mechanism(loginInfo));
        synchronized (this.mStanzaQueue) {
            this.stanzasSentBeforeAuthentication = this.stanzasSent;
            tagWriter.writeElement(authenticate);
        }
    }

    private static boolean isFastTokenAvailable(final Authentication authentication) {
        final var inline = authentication == null ? null : authentication.getInline();
        return inline != null && inline.hasExtension(Fast.class);
    }

    private void validate(
            final @Nullable SaslMechanism saslMechanism, Collection<String> mechanisms)
            throws StateChangingException {
        if (saslMechanism == null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": unable to find supported SASL mechanism in "
                            + mechanisms);
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
        checkRequireChannelBinding(saslMechanism);
        if (SaslMechanism.hashedToken(saslMechanism)) {
            return;
        }
        final int pinnedMechanism = account.getPinnedMechanismPriority();
        if (pinnedMechanism > saslMechanism.getPriority()) {
            Log.e(
                    Config.LOGTAG,
                    "Auth failed. Authentication mechanism "
                            + saslMechanism.getMechanism()
                            + " has lower priority ("
                            + saslMechanism.getPriority()
                            + ") than pinned priority ("
                            + pinnedMechanism
                            + "). Possible downgrade attack?");
            throw new StateChangingException(Account.State.DOWNGRADE_ATTACK);
        }
    }

    private void checkRequireChannelBinding(@NonNull final SaslMechanism mechanism)
            throws StateChangingException {
        if (appSettings.isRequireChannelBinding()) {
            if (mechanism instanceof ChannelBindingMechanism) {
                return;
            }
            Log.d(Config.LOGTAG, account.getJid() + ": server did not offer channel binding");
            throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
        }
    }

    private void checkAssignedDomainOrThrow(final Jid jid) throws StateChangingException {
        if (jid == null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": bind response is missing jid");
            throw new StateChangingException(Account.State.BIND_FAILURE);
        }
        final var current = this.account.getJid().getDomain();
        if (jid.getDomain().equals(current)) {
            return;
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": server tried to re-assign domain to "
                        + jid.getDomain());
        throw new StateChangingException(Account.State.BIND_FAILURE);
    }

    private void checkAssignedDomain(final Jid jid) {
        try {
            checkAssignedDomainOrThrow(jid);
        } catch (final StateChangingException e) {
            throw new StateChangingError(e.state);
        }
    }

    private AuthenticationRequest generateAuthenticationRequest(
            final String firstMessage, final boolean usingFast) {
        return generateAuthenticationRequest(
                firstMessage, usingFast, null, Bind2.QUICKSTART_FEATURES, true);
    }

    private AuthenticationRequest generateAuthenticationRequest(
            final String firstMessage,
            final boolean usingFast,
            final HashedToken.Mechanism hashedTokenRequest,
            final Collection<String> bind,
            final boolean inlineStreamManagement) {
        final var authenticate = new Authenticate();
        if (!Strings.isNullOrEmpty(firstMessage)) {
            authenticate.addChild("initial-response").setContent(firstMessage);
        }
        final var userAgent =
                authenticate.addExtension(
                        new UserAgent(
                                AccountUtils.publicDeviceId(
                                        account, appSettings.getInstallationId())));
        userAgent.setSoftware(
                String.format("%s %s", BuildConfig.APP_NAME, BuildConfig.VERSION_NAME));
        if (!PhoneHelper.isEmulator()) {
            userAgent.setDevice(String.format("%s %s", Build.MANUFACTURER, Build.MODEL));
        }
        // do not include bind if 'inlineStreamManagement' is missing and we have a streamId
        // (because we would rather just do a normal SM/resume)
        final boolean mayAttemptBind = streamId == null || inlineStreamManagement;
        if (bind != null && mayAttemptBind) {
            authenticate.addChild(generateBindRequest(bind));
        }
        if (inlineStreamManagement && streamId != null) {
            final var resume = new Resume(this.streamId.id, stanzasReceived);
            this.mSmCatchupMessageCounter.set(0);
            this.mWaitingForSmCatchup.set(true);
            authenticate.addExtension(resume);
        }
        if (hashedTokenRequest != null) {
            authenticate.addExtension(new RequestToken(hashedTokenRequest));
        }
        if (usingFast) {
            authenticate.addExtension(new Fast());
        }
        return authenticate;
    }

    private Bind generateBindRequest(final Collection<String> bindFeatures) {
        Log.d(Config.LOGTAG, "inline bind features: " + bindFeatures);
        final var bind = new Bind();
        bind.setTag(BuildConfig.APP_NAME);
        if (bindFeatures.contains(Namespace.CARBONS)) {
            bind.addExtension(new im.conversations.android.xmpp.model.carbons.Enable());
        }
        if (bindFeatures.contains(Namespace.STREAM_MANAGEMENT)) {
            bind.addExtension(new Enable());
        }
        return bind;
    }

    private void register() {
        final String preAuth = account.getKey(Account.KEY_PRE_AUTH_REGISTRATION_TOKEN);
        if (preAuth != null && features.invite()) {
            final Iq preAuthRequest = new Iq(Iq.Type.SET);
            preAuthRequest.addChild("preauth", Namespace.PARS).setAttribute("token", preAuth);
            sendUnmodifiedIqPacket(
                    preAuthRequest,
                    (response) -> {
                        if (response.getType() == Iq.Type.RESULT) {
                            sendRegistryRequest();
                        } else {
                            final String error = response.getErrorCondition();
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": failed to pre auth. "
                                            + error);
                            throw new StateChangingError(Account.State.REGISTRATION_INVALID_TOKEN);
                        }
                    },
                    true);
        } else {
            sendRegistryRequest();
        }
    }

    private void sendRegistryRequest() {
        final Iq register = new Iq(Iq.Type.GET);
        register.query(Namespace.REGISTER);
        register.setTo(account.getDomain());
        sendUnmodifiedIqPacket(
                register,
                (packet) -> {
                    if (packet.getType() == Iq.Type.TIMEOUT) {
                        return;
                    }
                    if (packet.getType() == Iq.Type.ERROR) {
                        throw new StateChangingError(Account.State.REGISTRATION_FAILED);
                    }
                    final Element query = packet.query(Namespace.REGISTER);
                    if (query.hasChild("username") && (query.hasChild("password"))) {
                        final Iq register1 = new Iq(Iq.Type.SET);
                        final Element username =
                                new Element("username").setContent(account.getUsername());
                        final Element password =
                                new Element("password").setContent(account.getPassword());
                        register1.query(Namespace.REGISTER).addChild(username);
                        register1.query().addChild(password);
                        register1.setFrom(account.getJid().asBareJid());
                        sendUnmodifiedIqPacket(register1, this::processRegistrationResponse, true);
                    } else if (query.hasChild("x", Namespace.DATA)) {
                        final Data data = Data.parse(query.findChild("x", Namespace.DATA));
                        final Element blob = query.findChild("data", "urn:xmpp:bob");
                        final String id = packet.getId();
                        InputStream is;
                        if (blob != null) {
                            try {
                                final String base64Blob = blob.getContent();
                                final byte[] strBlob = Base64.decode(base64Blob, Base64.DEFAULT);
                                is = new ByteArrayInputStream(strBlob);
                            } catch (Exception e) {
                                is = null;
                            }
                        } else {
                            final boolean useTor =
                                    mXmppConnectionService.useTorToConnect() || account.isOnion();
                            try {
                                final String url = data.getValue("url");
                                final String fallbackUrl = data.getValue("captcha-fallback-url");
                                if (url != null) {
                                    is = HttpConnectionManager.open(url, useTor);
                                } else if (fallbackUrl != null) {
                                    is = HttpConnectionManager.open(fallbackUrl, useTor);
                                } else {
                                    is = null;
                                }
                            } catch (final IOException e) {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid() + ": unable to fetch captcha",
                                        e);
                                is = null;
                            }
                        }

                        if (is != null) {
                            Bitmap captcha = BitmapFactory.decodeStream(is);
                            try {
                                if (mXmppConnectionService.displayCaptchaRequest(
                                        account, id, data, captcha)) {
                                    return;
                                }
                            } catch (Exception e) {
                                throw new StateChangingError(Account.State.REGISTRATION_FAILED);
                            }
                        }
                        throw new StateChangingError(Account.State.REGISTRATION_FAILED);
                    } else if (query.hasChild("instructions")
                            || query.hasChild("x", Namespace.OOB)) {
                        final String instructions = query.findChildContent("instructions");
                        final Element oob = query.findChild("x", Namespace.OOB);
                        final String url = oob == null ? null : oob.findChildContent("url");
                        if (url != null) {
                            setAccountCreationFailed(url);
                        } else if (instructions != null) {
                            final Matcher matcher = Patterns.AUTOLINK_WEB_URL.matcher(instructions);
                            if (matcher.find()) {
                                setAccountCreationFailed(
                                        instructions.substring(matcher.start(), matcher.end()));
                            }
                        }
                        throw new StateChangingError(Account.State.REGISTRATION_FAILED);
                    }
                },
                true);
    }

    public void sendCreateAccountWithCaptchaPacket(final String id, final Data data) {
        final Iq request = IqGenerator.generateCreateAccountWithCaptcha(account, id, data);
        this.sendUnmodifiedIqPacket(request, this::processRegistrationResponse, true);
    }

    private void processRegistrationResponse(final Iq response) {
        if (response.getType() == Iq.Type.RESULT) {
            account.setOption(Account.OPTION_REGISTER, false);
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": successfully registered new account on server");
            throw new StateChangingError(Account.State.REGISTRATION_SUCCESSFUL);
        } else {
            final Account.State state = getRegistrationFailedState(response);
            throw new StateChangingError(state);
        }
    }

    @NonNull
    private static Account.State getRegistrationFailedState(final Iq response) {
        final List<String> PASSWORD_TOO_WEAK_MESSAGES =
                Arrays.asList("The password is too weak", "Please use a longer password.");
        final var error = response.getError();
        final var condition = error == null ? null : error.getCondition();
        final Account.State state;
        if (condition instanceof Condition.Conflict) {
            state = Account.State.REGISTRATION_CONFLICT;
        } else if (condition instanceof Condition.ResourceConstraint) {
            state = Account.State.REGISTRATION_PLEASE_WAIT;
        } else if (condition instanceof Condition.NotAcceptable
                && PASSWORD_TOO_WEAK_MESSAGES.contains(error.getTextAsString())) {
            state = Account.State.REGISTRATION_PASSWORD_TOO_WEAK;
        } else {
            state = Account.State.REGISTRATION_FAILED;
        }
        return state;
    }

    private void setAccountCreationFailed(final String url) {
        final HttpUrl httpUrl = url == null ? null : HttpUrl.parse(url);
        if (httpUrl != null && httpUrl.isHttps()) {
            this.redirectionUrl = httpUrl;
            throw new StateChangingError(Account.State.REGISTRATION_WEB);
        }
        throw new StateChangingError(Account.State.REGISTRATION_FAILED);
    }

    public HttpUrl getRedirectionUrl() {
        return this.redirectionUrl;
    }

    public void resetEverything() {
        resetAttemptCount(true);
        resetStreamId();
        clearIqCallbacks();
        synchronized (this.mStanzaQueue) {
            this.stanzasSent = 0;
            this.mStanzaQueue.clear();
        }
        this.redirectionUrl = null;
        synchronized (this.disco) {
            disco.clear();
        }
        synchronized (this.commands) {
            this.commands.clear();
        }
        this.loginInfo = null;
    }

    private void sendBindRequest() {
        try {
            mXmppConnectionService.restoredFromDatabaseLatch.await();
        } catch (InterruptedException e) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": interrupted while waiting for DB restore during bind");
            return;
        }
        clearIqCallbacks();
        if (account.getJid().isBareJid()) {
            account.setResource(createNewResource());
        } else {
            fixResource(mXmppConnectionService, account);
        }
        final Iq iq = new Iq(Iq.Type.SET);
        final String resource =
                Config.USE_RANDOM_RESOURCE_ON_EVERY_BIND
                        ? CryptoHelper.random(9)
                        : account.getResource();
        iq.addExtension(new im.conversations.android.xmpp.model.bind.Bind()).setResource(resource);
        this.sendUnmodifiedIqPacket(
                iq,
                (packet) -> {
                    if (packet.getType() == Iq.Type.TIMEOUT) {
                        return;
                    }
                    final var bind =
                            packet.getExtension(
                                    im.conversations.android.xmpp.model.bind.Bind.class);
                    if (bind != null && packet.getType() == Iq.Type.RESULT) {
                        isBound = true;
                        final Jid assignedJid = bind.getJid();
                        checkAssignedDomain(assignedJid);
                        if (account.setJid(assignedJid)) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": jid changed during bind. updating database");
                            mXmppConnectionService.databaseBackend.updateAccount(account);
                        }
                        if (streamFeatures.hasChild("session")
                                && !streamFeatures.findChild("session").hasChild("optional")) {
                            sendStartSession();
                        } else {
                            final boolean waitForDisco = enableStreamManagement();
                            sendPostBindInitialization(waitForDisco, false);
                        }
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid()
                                        + ": disconnecting because of bind failure ("
                                        + packet);
                        final var error = packet.getError();
                        // TODO error.is(Condition)
                        if (packet.getType() == Iq.Type.ERROR
                                && error != null
                                && error.hasChild("conflict")) {
                            account.setResource(createNewResource());
                        }
                        throw new StateChangingError(Account.State.BIND_FAILURE);
                    }
                },
                true);
    }

    private void clearIqCallbacks() {
        final Iq failurePacket = new Iq(Iq.Type.TIMEOUT);
        final ArrayList<Consumer<Iq>> callbacks = new ArrayList<>();
        synchronized (this.packetCallbacks) {
            if (this.packetCallbacks.isEmpty()) {
                return;
            }
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": clearing "
                            + this.packetCallbacks.size()
                            + " iq callbacks");
            final var iterator = this.packetCallbacks.values().iterator();
            while (iterator.hasNext()) {
                final var entry = iterator.next();
                callbacks.add(entry.second);
                iterator.remove();
            }
        }
        for (final var callback : callbacks) {
            try {
                callback.accept(failurePacket);
            } catch (StateChangingError error) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": caught StateChangingError("
                                + error.state.toString()
                                + ") while clearing callbacks");
                // ignore
            }
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": done clearing iq callbacks. "
                        + this.packetCallbacks.size()
                        + " left");
    }

    public void sendDiscoTimeout() {
        if (mWaitForDisco.compareAndSet(true, false)) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": finalizing bind after disco timeout");
            finalizeBind();
        }
    }

    private void sendStartSession() {
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": sending legacy session to outdated server");
        final Iq startSession = new Iq(Iq.Type.SET);
        startSession.addChild("session", "urn:ietf:params:xml:ns:xmpp-session");
        this.sendUnmodifiedIqPacket(
                startSession,
                (packet) -> {
                    if (packet.getType() == Iq.Type.RESULT) {
                        final boolean waitForDisco = enableStreamManagement();
                        sendPostBindInitialization(waitForDisco, false);
                    } else if (packet.getType() != Iq.Type.TIMEOUT) {
                        throw new StateChangingError(Account.State.SESSION_FAILURE);
                    }
                },
                true);
    }

    private boolean enableStreamManagement() {
        final boolean streamManagement = this.streamFeatures.streamManagement();
        if (streamManagement) {
            synchronized (this.mStanzaQueue) {
                final var enable = new Enable();
                tagWriter.writeStanzaAsync(enable);
                stanzasSent = 0;
                mStanzaQueue.clear();
            }
            return true;
        } else {
            return false;
        }
    }

    private void sendPostBindInitialization(
            final boolean waitForDisco, final boolean carbonsEnabled) {
        features.carbonsEnabled = carbonsEnabled;
        features.blockListRequested = false;
        synchronized (this.disco) {
            this.disco.clear();
        }
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": starting service discovery");
        mPendingServiceDiscoveries.set(0);
        mWaitForDisco.set(waitForDisco);
        lastDiscoStarted = SystemClock.elapsedRealtime();
        mXmppConnectionService.scheduleWakeUpCall(
                Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
        final Element caps = streamFeatures.findChild("c");
        final String hash = caps == null ? null : caps.getAttribute("hash");
        final String ver = caps == null ? null : caps.getAttribute("ver");
        ServiceDiscoveryResult discoveryResult = null;
        if (hash != null && ver != null) {
            discoveryResult =
                    mXmppConnectionService.getCachedServiceDiscoveryResult(new Pair<>(hash, ver));
        }
        final boolean requestDiscoItemsFirst =
                !account.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY);
        if (requestDiscoItemsFirst) {
            sendServiceDiscoveryItems(account.getDomain());
        }
        if (discoveryResult == null) {
            sendServiceDiscoveryInfo(account.getDomain());
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": server caps came from cache");
            disco.put(account.getDomain(), discoveryResult);
        }
        discoverMamPreferences();
        sendServiceDiscoveryInfo(account.getJid().asBareJid());
        if (!requestDiscoItemsFirst) {
            sendServiceDiscoveryItems(account.getDomain());
        }

        if (!mWaitForDisco.get()) {
            finalizeBind();
        }
        this.lastSessionStarted = SystemClock.elapsedRealtime();
    }

    private void sendServiceDiscoveryInfo(final Jid jid) {
        mPendingServiceDiscoveries.incrementAndGet();
        final Iq iq = new Iq(Iq.Type.GET);
        iq.setTo(jid);
        iq.query("http://jabber.org/protocol/disco#info");
        this.sendIqPacket(
                iq,
                (packet) -> {
                    if (packet.getType() == Iq.Type.RESULT) {
                        boolean advancedStreamFeaturesLoaded;
                        synchronized (XmppConnection.this.disco) {
                            ServiceDiscoveryResult result = new ServiceDiscoveryResult(packet);
                            if (jid.equals(account.getDomain())) {
                                mXmppConnectionService.databaseBackend.insertDiscoveryResult(
                                        result);
                            }
                            disco.put(jid, result);
                            advancedStreamFeaturesLoaded =
                                    disco.containsKey(account.getDomain())
                                            && disco.containsKey(account.getJid().asBareJid());
                        }
                        if (advancedStreamFeaturesLoaded
                                && (jid.equals(account.getDomain())
                                        || jid.equals(account.getJid().asBareJid()))) {
                            enableAdvancedStreamFeatures();
                        }
                    } else if (packet.getType() == Iq.Type.ERROR) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": could not query disco info for "
                                        + jid.toString());
                        final boolean serverOrAccount =
                                jid.equals(account.getDomain())
                                        || jid.equals(account.getJid().asBareJid());
                        final boolean advancedStreamFeaturesLoaded;
                        if (serverOrAccount) {
                            synchronized (XmppConnection.this.disco) {
                                disco.put(jid, ServiceDiscoveryResult.empty());
                                advancedStreamFeaturesLoaded =
                                        disco.containsKey(account.getDomain())
                                                && disco.containsKey(account.getJid().asBareJid());
                            }
                        } else {
                            advancedStreamFeaturesLoaded = false;
                        }
                        if (advancedStreamFeaturesLoaded) {
                            enableAdvancedStreamFeatures();
                        }
                    }
                    if (packet.getType() != Iq.Type.TIMEOUT) {
                        if (mPendingServiceDiscoveries.decrementAndGet() == 0
                                && mWaitForDisco.compareAndSet(true, false)) {
                            finalizeBind();
                        }
                    }
                });
    }

    private void discoverMamPreferences() {
        final Iq request = new Iq(Iq.Type.GET);
        request.addChild("prefs", MessageArchiveService.Version.MAM_2.namespace);
        sendIqPacket(
                request,
                (response) -> {
                    if (response.getType() == Iq.Type.RESULT) {
                        Element prefs =
                                response.findChild(
                                        "prefs", MessageArchiveService.Version.MAM_2.namespace);
                        isMamPreferenceAlways =
                                "always"
                                        .equals(
                                                prefs == null
                                                        ? null
                                                        : prefs.getAttribute("default"));
                    }
                });
    }

    private void discoverCommands() {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(account.getDomain());
        request.addChild("query", Namespace.DISCO_ITEMS).setAttribute("node", Namespace.COMMANDS);
        sendIqPacket(
                request,
                (response) -> {
                    if (response.getType() == Iq.Type.RESULT) {
                        final Element query = response.findChild("query", Namespace.DISCO_ITEMS);
                        if (query == null) {
                            return;
                        }
                        final HashMap<String, Jid> commands = new HashMap<>();
                        for (final Element child : query.getChildren()) {
                            if ("item".equals(child.getName())) {
                                final String node = child.getAttribute("node");
                                final Jid jid = child.getAttributeAsJid("jid");
                                if (node != null && jid != null) {
                                    commands.put(node, jid);
                                }
                            }
                        }
                        synchronized (this.commands) {
                            this.commands.clear();
                            this.commands.putAll(commands);
                        }
                    }
                });
    }

    public boolean isMamPreferenceAlways() {
        return isMamPreferenceAlways;
    }

    private void finalizeBind() {
        this.offlineMessagesRetrieved = false;
        this.bindListener.run();
        this.changeStatusToOnline();
    }

    private void enableAdvancedStreamFeatures() {
        if (getFeatures().blocking() && !features.blockListRequested) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": Requesting block list");
            this.sendIqPacket(getIqGenerator().generateGetBlockList(), unregisteredIqListener);
        }
        for (final OnAdvancedStreamFeaturesLoaded listener :
                advancedStreamFeaturesLoadedListeners) {
            listener.onAdvancedStreamFeaturesAvailable(account);
        }
        if (getFeatures().carbons() && !features.carbonsEnabled) {
            sendEnableCarbons();
        }
        if (getFeatures().commands()) {
            discoverCommands();
        }
    }

    private void sendServiceDiscoveryItems(final Jid server) {
        mPendingServiceDiscoveries.incrementAndGet();
        final Iq iq = new Iq(Iq.Type.GET);
        iq.setTo(server.getDomain());
        iq.query("http://jabber.org/protocol/disco#items");
        this.sendIqPacket(
                iq,
                (packet) -> {
                    if (packet.getType() == Iq.Type.RESULT) {
                        final HashSet<Jid> items = new HashSet<>();
                        final List<Element> elements = packet.query().getChildren();
                        for (final Element element : elements) {
                            if (element.getName().equals("item")) {
                                final Jid jid =
                                        InvalidJid.getNullForInvalid(
                                                element.getAttributeAsJid("jid"));
                                if (jid != null && !jid.equals(account.getDomain())) {
                                    items.add(jid);
                                }
                            }
                        }
                        for (Jid jid : items) {
                            sendServiceDiscoveryInfo(jid);
                        }
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": could not query disco items of "
                                        + server);
                    }
                    if (packet.getType() != Iq.Type.TIMEOUT) {
                        if (mPendingServiceDiscoveries.decrementAndGet() == 0
                                && mWaitForDisco.compareAndSet(true, false)) {
                            finalizeBind();
                        }
                    }
                });
    }

    private void sendEnableCarbons() {
        final Iq iq = new Iq(Iq.Type.SET);
        iq.addChild("enable", Namespace.CARBONS);
        this.sendIqPacket(
                iq,
                (packet) -> {
                    if (packet.getType() == Iq.Type.RESULT) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": successfully enabled carbons");
                        features.carbonsEnabled = true;
                    } else {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": could not enable carbons "
                                        + packet);
                    }
                });
    }

    private void processStreamError(final Tag currentTag) throws IOException {
        final Element streamError = tagReader.readElement(currentTag);
        if (streamError == null) {
            return;
        }
        if (streamError.hasChild("conflict")) {
            final var loginInfo = this.loginInfo;
            if (loginInfo != null && loginInfo.saslVersion == SaslMechanism.Version.SASL_2) {
                this.appSettings.resetInstallationId();
            }
            account.setResource(createNewResource());
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": switching resource due to conflict ("
                            + account.getResource()
                            + ")");
            throw new IOException("Closed stream due to resource conflict");
        } else if (streamError.hasChild("host-unknown")) {
            throw new StateChangingException(Account.State.HOST_UNKNOWN);
        } else if (streamError.hasChild("policy-violation")) {
            this.lastConnect = SystemClock.elapsedRealtime();
            final String text = streamError.findChildContent("text");
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": policy violation. " + text);
            failPendingMessages(text);
            throw new StateChangingException(Account.State.POLICY_VIOLATION);
        } else if (streamError.hasChild("see-other-host")) {
            final String seeOtherHost = streamError.findChildContent("see-other-host");
            final Resolver.Result currentResolverResult = this.currentResolverResult;
            if (Strings.isNullOrEmpty(seeOtherHost) || currentResolverResult == null) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid() + ": stream error " + streamError);
                throw new StateChangingException(Account.State.STREAM_ERROR);
            }
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": see other host: "
                            + seeOtherHost
                            + " "
                            + currentResolverResult);
            final Resolver.Result seeOtherResult = currentResolverResult.seeOtherHost(seeOtherHost);
            if (seeOtherResult != null) {
                this.seeOtherHostResolverResult = seeOtherResult;
                throw new StateChangingException(Account.State.SEE_OTHER_HOST);
            } else {
                throw new StateChangingException(Account.State.STREAM_ERROR);
            }
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": stream error " + streamError);
            throw new StateChangingException(Account.State.STREAM_ERROR);
        }
    }

    private void failPendingMessages(final String error) {
        synchronized (this.mStanzaQueue) {
            for (int i = 0; i < mStanzaQueue.size(); ++i) {
                final Stanza stanza = mStanzaQueue.valueAt(i);
                if (stanza instanceof im.conversations.android.xmpp.model.stanza.Message packet) {
                    final String id = packet.getId();
                    final Jid to = packet.getTo();
                    mXmppConnectionService.markMessage(
                            account, to.asBareJid(), id, Message.STATUS_SEND_FAILED, error);
                }
            }
        }
    }

    private boolean establishStream(final SSLSockets.Version sslVersion)
            throws IOException, InterruptedException {
        final boolean secureConnection = sslVersion != SSLSockets.Version.NONE;
        final SaslMechanism quickStartMechanism;
        if (secureConnection) {
            quickStartMechanism =
                    SaslMechanism.ensureAvailable(
                            account.getQuickStartMechanism(),
                            sslVersion,
                            appSettings.isRequireChannelBinding());
        } else {
            quickStartMechanism = null;
        }
        if (secureConnection
                && Config.QUICKSTART_ENABLED
                && quickStartMechanism != null
                && account.isOptionSet(Account.OPTION_QUICKSTART_AVAILABLE)) {
            mXmppConnectionService.restoredFromDatabaseLatch.await();
            this.loginInfo =
                    new LoginInfo(
                            quickStartMechanism,
                            SaslMechanism.Version.SASL_2,
                            Bind2.QUICKSTART_FEATURES);
            final boolean usingFast = quickStartMechanism instanceof HashedToken;
            final AuthenticationRequest authenticate =
                    generateAuthenticationRequest(
                            quickStartMechanism.getClientFirstMessage(sslSocketOrNull(this.socket)),
                            usingFast);
            authenticate.setMechanism(quickStartMechanism);
            sendStartStream(true, false);
            synchronized (this.mStanzaQueue) {
                this.stanzasSentBeforeAuthentication = this.stanzasSent;
                tagWriter.writeElement(authenticate);
            }
            Log.d(
                    Config.LOGTAG,
                    account.getJid().toString()
                            + ": quick start with "
                            + quickStartMechanism.getMechanism());
            return true;
        } else {
            sendStartStream(secureConnection, true);
            return false;
        }
    }

    private void sendStartStream(final boolean from, final boolean flush) throws IOException {
        final Tag stream = Tag.start("stream:stream");
        stream.setAttribute("to", account.getServer());
        if (from) {
            stream.setAttribute("from", account.getJid().asBareJid().toEscapedString());
        }
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", LocalizedContent.STREAM_LANGUAGE);
        stream.setAttribute("xmlns", Namespace.JABBER_CLIENT);
        stream.setAttribute("xmlns:stream", Namespace.STREAMS);
        tagWriter.writeTag(stream, flush);
    }

    private static String createNewResource() {
        return String.format("%s.%s", BuildConfig.APP_NAME, CryptoHelper.random(3));
    }

    public String sendIqPacket(final Iq packet, final Consumer<Iq> callback) {
        packet.setFrom(account.getJid());
        return this.sendUnmodifiedIqPacket(packet, callback, false);
    }

    public synchronized String sendUnmodifiedIqPacket(
            final Iq packet, final Consumer<Iq> callback, boolean force) {
        // TODO if callback != null verify that type is get or set
        if (packet.getId() == null) {
            packet.setId(CryptoHelper.random(9));
        }
        if (callback != null) {
            synchronized (this.packetCallbacks) {
                packetCallbacks.put(packet.getId(), new Pair<>(packet, callback));
            }
        }
        this.sendPacket(packet, force);
        return packet.getId();
    }

    public void sendMessagePacket(final im.conversations.android.xmpp.model.stanza.Message packet) {
        this.sendPacket(packet);
    }

    public void sendPresencePacket(final Presence packet) {
        this.sendPacket(packet);
    }

    private synchronized void sendPacket(final StreamElement packet) {
        sendPacket(packet, false);
    }

    private synchronized void sendPacket(final StreamElement packet, final boolean force) {
        if (stanzasSent == Integer.MAX_VALUE) {
            resetStreamId();
            disconnect(true);
            return;
        }
        synchronized (this.mStanzaQueue) {
            if (force || isBound) {
                tagWriter.writeStanzaAsync(packet);
            } else {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + " do not write stanza to unbound stream "
                                + packet.toString());
            }
            if (packet instanceof Stanza stanza) {
                if (this.mStanzaQueue.size() != 0) {
                    int currentHighestKey = this.mStanzaQueue.keyAt(this.mStanzaQueue.size() - 1);
                    if (currentHighestKey != stanzasSent) {
                        throw new AssertionError("Stanza count messed up");
                    }
                }

                ++stanzasSent;
                if (Config.EXTENDED_SM_LOGGING) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": counting outbound "
                                    + packet.getName()
                                    + " as #"
                                    + stanzasSent);
                }
                this.mStanzaQueue.append(stanzasSent, stanza);
                if (stanza instanceof im.conversations.android.xmpp.model.stanza.Message
                        && stanza.getId() != null
                        && inSmacksSession) {
                    if (Config.EXTENDED_SM_LOGGING) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": requesting ack for message stanza #"
                                        + stanzasSent);
                    }
                    tagWriter.writeStanzaAsync(new Request());
                }
            }
        }
    }

    public void sendPing() {
        if (!r()) {
            final Iq iq = new Iq(Iq.Type.GET);
            iq.setFrom(account.getJid());
            iq.addChild("ping", Namespace.PING);
            this.sendIqPacket(iq, null);
        }
        this.lastPingSent = SystemClock.elapsedRealtime();
    }

    public void setOnJinglePacketReceivedListener(final OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(final OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnMessageAcknowledgeListener(final OnMessageAcknowledged listener) {
        this.acknowledgedListener = listener;
    }

    public void addOnAdvancedStreamFeaturesAvailableListener(
            final OnAdvancedStreamFeaturesLoaded listener) {
        this.advancedStreamFeaturesLoadedListeners.add(listener);
    }

    private void forceCloseSocket() {
        FileBackend.close(this.socket);
        FileBackend.close(this.tagReader);
    }

    public void interrupt() {
        if (this.mThread != null) {
            this.mThread.interrupt();
        }
    }

    public void disconnect(final boolean force) {
        interrupt();
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": disconnecting force=" + force);
        if (force) {
            forceCloseSocket();
        } else {
            final TagWriter currentTagWriter = this.tagWriter;
            if (currentTagWriter.isActive()) {
                currentTagWriter.finish();
                final Socket currentSocket = this.socket;
                final CountDownLatch streamCountDownLatch = this.mStreamCountDownLatch;
                try {
                    currentTagWriter.await(1, TimeUnit.SECONDS);
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": closing stream");
                    currentTagWriter.writeTag(Tag.end("stream:stream"));
                    if (streamCountDownLatch != null) {
                        if (streamCountDownLatch.await(1, TimeUnit.SECONDS)) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid() + ": remote ended stream");
                        } else {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": remote has not closed socket. force closing");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": interrupted while gracefully closing stream");
                } catch (final IOException e) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": io exception during disconnect ("
                                    + e.getMessage()
                                    + ")");
                } finally {
                    FileBackend.close(currentSocket);
                }
            } else {
                forceCloseSocket();
            }
        }
    }

    private void resetStreamId() {
        this.streamId = null;
        this.boundStreamFeatures = null;
    }

    private List<Entry<Jid, ServiceDiscoveryResult>> findDiscoItemsByFeature(final String feature) {
        synchronized (this.disco) {
            final List<Entry<Jid, ServiceDiscoveryResult>> items = new ArrayList<>();
            for (final Entry<Jid, ServiceDiscoveryResult> cursor : this.disco.entrySet()) {
                if (cursor.getValue().getFeatures().contains(feature)) {
                    items.add(cursor);
                }
            }
            return items;
        }
    }

    public Jid findDiscoItemByFeature(final String feature) {
        final List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(feature);
        if (items.size() >= 1) {
            return items.get(0).getKey();
        }
        return null;
    }

    public boolean r() {
        if (getFeatures().sm()) {
            this.tagWriter.writeStanzaAsync(new Request());
            return true;
        } else {
            return false;
        }
    }

    public List<String> getMucServersWithholdAccount() {
        final List<String> servers = getMucServers();
        servers.remove(account.getDomain().toEscapedString());
        return servers;
    }

    public List<String> getMucServers() {
        List<String> servers = new ArrayList<>();
        synchronized (this.disco) {
            for (final Entry<Jid, ServiceDiscoveryResult> cursor : disco.entrySet()) {
                final ServiceDiscoveryResult value = cursor.getValue();
                if (value.getFeatures().contains("http://jabber.org/protocol/muc")
                        && value.hasIdentity("conference", "text")
                        && !value.getFeatures().contains("jabber:iq:gateway")
                        && !value.hasIdentity("conference", "irc")) {
                    servers.add(cursor.getKey().toString());
                }
            }
        }
        return servers;
    }

    public String getMucServer() {
        List<String> servers = getMucServers();
        return servers.size() > 0 ? servers.get(0) : null;
    }

    public int getTimeToNextAttempt(final boolean aggressive) {
        final int interval;
        if (aggressive) {
            interval = Math.min((int) (3 * Math.pow(1.3, attempt)), 60);
        } else {
            final int additionalTime =
                    account.getLastErrorStatus() == Account.State.POLICY_VIOLATION ? 3 : 0;
            interval = Math.min((int) (25 * Math.pow(1.3, (additionalTime + attempt))), 300);
        }
        final int secondsSinceLast =
                (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
        return interval - secondsSinceLast;
    }

    public int getAttempt() {
        return this.attempt;
    }

    public Features getFeatures() {
        return this.features;
    }

    public long getLastSessionEstablished() {
        final long diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
        return System.currentTimeMillis() - diff;
    }

    public long getLastConnect() {
        return this.lastConnect;
    }

    public long getLastPingSent() {
        return this.lastPingSent;
    }

    public long getLastDiscoStarted() {
        return this.lastDiscoStarted;
    }

    public long getLastPacketReceived() {
        return this.lastPacketReceived;
    }

    public void sendActive() {
        this.sendPacket(new Active());
    }

    public void sendInactive() {
        this.sendPacket(new Inactive());
    }

    public void resetAttemptCount(boolean resetConnectTime) {
        this.attempt = 0;
        if (resetConnectTime) {
            this.lastConnect = 0;
        }
    }

    public void setInteractive(boolean interactive) {
        this.mInteractive = interactive;
    }

    private IqGenerator getIqGenerator() {
        return mXmppConnectionService.getIqGenerator();
    }

    public void trackOfflineMessageRetrieval(boolean trackOfflineMessageRetrieval) {
        if (trackOfflineMessageRetrieval) {
            final Iq iqPing = new Iq(Iq.Type.GET);
            iqPing.addChild("ping", Namespace.PING);
            this.sendIqPacket(
                    iqPing,
                    (response) -> {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid()
                                        + ": got ping response after sending initial presence");
                        XmppConnection.this.offlineMessagesRetrieved = true;
                    });
        } else {
            this.offlineMessagesRetrieved = true;
        }
    }

    public boolean isOfflineMessagesRetrieved() {
        return this.offlineMessagesRetrieved;
    }

    public void fetchRoster() {
        final Iq iqPacket = new Iq(Iq.Type.GET);
        final var version = account.getRosterVersion();
        if (Strings.isNullOrEmpty(account.getRosterVersion())) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": fetching roster");
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": fetching roster version " + version);
        }
        iqPacket.query(Namespace.ROSTER).setAttribute("ver", version);
        sendIqPacket(iqPacket, unregisteredIqListener);
    }

    private class MyKeyManager implements X509KeyManager {
        @Override
        public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
            return account.getPrivateKeyAlias();
        }

        @Override
        public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            Log.d(Config.LOGTAG, "getting certificate chain");
            try {
                return KeyChain.getCertificateChain(mXmppConnectionService, alias);
            } catch (final Exception e) {
                Log.d(Config.LOGTAG, "could not get certificate chain", e);
                return new X509Certificate[0];
            }
        }

        @Override
        public String[] getClientAliases(String s, Principal[] principals) {
            final String alias = account.getPrivateKeyAlias();
            return alias != null ? new String[] {alias} : new String[0];
        }

        @Override
        public String[] getServerAliases(String s, Principal[] principals) {
            return new String[0];
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            try {
                return KeyChain.getPrivateKey(mXmppConnectionService, alias);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static class LoginInfo {
        public final SaslMechanism saslMechanism;
        public final SaslMechanism.Version saslVersion;
        public final List<String> inlineBindFeatures;
        public final AtomicBoolean success = new AtomicBoolean(false);

        private LoginInfo(
                final SaslMechanism saslMechanism,
                final SaslMechanism.Version saslVersion,
                final Collection<String> inlineBindFeatures) {
            Preconditions.checkNotNull(saslMechanism, "SASL Mechanism must not be null");
            Preconditions.checkNotNull(saslVersion, "SASL version must not be null");
            this.saslMechanism = saslMechanism;
            this.saslVersion = saslVersion;
            this.inlineBindFeatures =
                    inlineBindFeatures == null
                            ? Collections.emptyList()
                            : ImmutableList.copyOf(inlineBindFeatures);
        }

        public static SaslMechanism mechanism(final LoginInfo loginInfo) {
            return loginInfo == null ? null : loginInfo.saslMechanism;
        }

        public void success(final String challenge, final SSLSocket sslSocket)
                throws SaslMechanism.AuthenticationException {
            final var response = this.saslMechanism.getResponse(challenge, sslSocket);
            if (!Strings.isNullOrEmpty(response)) {
                throw new SaslMechanism.AuthenticationException(
                        "processing success yielded another response");
            }
            if (this.success.compareAndSet(false, true)) {
                return;
            }
            throw new SaslMechanism.AuthenticationException("Process 'success' twice");
        }

        public static boolean isSuccess(final LoginInfo loginInfo) {
            return loginInfo != null && loginInfo.success.get();
        }
    }

    private static class StreamId {
        public final String id;
        public final Resolver.Result location;

        private StreamId(String id, Resolver.Result location) {
            this.id = id;
            this.location = location;
        }

        @NonNull
        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("location", location)
                    .toString();
        }
    }

    private static class StateChangingError extends Error {
        private final Account.State state;

        public StateChangingError(Account.State state) {
            this.state = state;
        }
    }

    private static class StateChangingException extends IOException {
        private final Account.State state;

        public StateChangingException(Account.State state) {
            this.state = state;
        }
    }

    public class Features {
        XmppConnection connection;
        private boolean carbonsEnabled = false;
        private boolean encryptionEnabled = false;
        private boolean blockListRequested = false;

        public Features(final XmppConnection connection) {
            this.connection = connection;
        }

        private boolean hasDiscoFeature(final Jid server, final String feature) {
            synchronized (XmppConnection.this.disco) {
                final ServiceDiscoveryResult sdr = connection.disco.get(server);
                return sdr != null && sdr.getFeatures().contains(feature);
            }
        }

        public boolean carbons() {
            return hasDiscoFeature(account.getDomain(), Namespace.CARBONS);
        }

        public boolean commands() {
            return hasDiscoFeature(account.getDomain(), Namespace.COMMANDS);
        }

        public boolean easyOnboardingInvites() {
            synchronized (commands) {
                return commands.containsKey(Namespace.EASY_ONBOARDING_INVITE);
            }
        }

        public boolean bookmarksConversion() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.BOOKMARKS_CONVERSION)
                    && pepPublishOptions();
        }

        public boolean blocking() {
            return hasDiscoFeature(account.getDomain(), Namespace.BLOCKING);
        }

        public boolean spamReporting() {
            return hasDiscoFeature(account.getDomain(), Namespace.REPORTING);
        }

        public boolean flexibleOfflineMessageRetrieval() {
            return hasDiscoFeature(
                    account.getDomain(), Namespace.FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL);
        }

        public boolean register() {
            return hasDiscoFeature(account.getDomain(), Namespace.REGISTER);
        }

        public boolean invite() {
            return connection.streamFeatures != null
                    && connection.streamFeatures.hasChild("register", Namespace.INVITE);
        }

        public boolean sm() {
            return streamId != null
                    || (connection.streamFeatures != null
                            && connection.streamFeatures.streamManagement());
        }

        public boolean csi() {
            return connection.streamFeatures != null
                    && connection.streamFeatures.clientStateIndication();
        }

        public boolean pep() {
            synchronized (XmppConnection.this.disco) {
                ServiceDiscoveryResult info = disco.get(account.getJid().asBareJid());
                return info != null && info.hasIdentity("pubsub", "pep");
            }
        }

        public boolean pepPersistent() {
            synchronized (XmppConnection.this.disco) {
                ServiceDiscoveryResult info = disco.get(account.getJid().asBareJid());
                return info != null
                        && info.getFeatures()
                                .contains("http://jabber.org/protocol/pubsub#persistent-items");
            }
        }

        public boolean bind2() {
            final var loginInfo = XmppConnection.this.loginInfo;
            return loginInfo != null && !loginInfo.inlineBindFeatures.isEmpty();
        }

        public boolean sasl2() {
            final var loginInfo = XmppConnection.this.loginInfo;
            return loginInfo != null && loginInfo.saslVersion == SaslMechanism.Version.SASL_2;
        }

        public String loginMechanism() {
            final var loginInfo = XmppConnection.this.loginInfo;
            return loginInfo == null ? null : loginInfo.saslMechanism.getMechanism();
        }

        public boolean pepPublishOptions() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.PUBSUB_PUBLISH_OPTIONS);
        }

        public boolean pepConfigNodeMax() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.PUBSUB_CONFIG_NODE_MAX);
        }

        public boolean pepOmemoWhitelisted() {
            return hasDiscoFeature(
                    account.getJid().asBareJid(), AxolotlService.PEP_OMEMO_WHITELISTED);
        }

        public boolean mam() {
            return MessageArchiveService.Version.has(getAccountFeatures());
        }

        public List<String> getAccountFeatures() {
            ServiceDiscoveryResult result = connection.disco.get(account.getJid().asBareJid());
            return result == null ? Collections.emptyList() : result.getFeatures();
        }

        public boolean push() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.PUSH)
                    || hasDiscoFeature(account.getDomain(), Namespace.PUSH);
        }

        public boolean rosterVersioning() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
        }

        public void setBlockListRequested(boolean value) {
            this.blockListRequested = value;
        }

        public boolean httpUpload(long filesize) {
            if (Config.DISABLE_HTTP_UPLOAD) {
                return false;
            } else {
                for (String namespace :
                        new String[] {Namespace.HTTP_UPLOAD, Namespace.HTTP_UPLOAD_LEGACY}) {
                    List<Entry<Jid, ServiceDiscoveryResult>> items =
                            findDiscoItemsByFeature(namespace);
                    if (items.size() > 0) {
                        try {
                            long maxsize =
                                    Long.parseLong(
                                            items.get(0)
                                                    .getValue()
                                                    .getExtendedDiscoInformation(
                                                            namespace, "max-file-size"));
                            if (filesize <= maxsize) {
                                return true;
                            } else {
                                Log.d(
                                        Config.LOGTAG,
                                        account.getJid().asBareJid()
                                                + ": http upload is not available for files with size "
                                                + filesize
                                                + " (max is "
                                                + maxsize
                                                + ")");
                                return false;
                            }
                        } catch (Exception e) {
                            return true;
                        }
                    }
                }
                return false;
            }
        }

        public boolean useLegacyHttpUpload() {
            return findDiscoItemByFeature(Namespace.HTTP_UPLOAD) == null
                    && findDiscoItemByFeature(Namespace.HTTP_UPLOAD_LEGACY) != null;
        }

        public long getMaxHttpUploadSize() {
            for (String namespace :
                    new String[] {Namespace.HTTP_UPLOAD, Namespace.HTTP_UPLOAD_LEGACY}) {
                List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(namespace);
                if (items.size() > 0) {
                    try {
                        return Long.parseLong(
                                items.get(0)
                                        .getValue()
                                        .getExtendedDiscoInformation(namespace, "max-file-size"));
                    } catch (Exception e) {
                        // ignored
                    }
                }
            }
            return -1;
        }

        public boolean stanzaIds() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.STANZA_IDS);
        }

        public boolean bookmarks2() {
            return pepPublishOptions()
                    && hasDiscoFeature(account.getJid().asBareJid(), Namespace.BOOKMARKS2_COMPAT);
        }

        public boolean externalServiceDiscovery() {
            return hasDiscoFeature(account.getDomain(), Namespace.EXTERNAL_SERVICE_DISCOVERY);
        }

        public boolean mds() {
            return pepPublishOptions()
                    && pepConfigNodeMax()
                    && Config.MESSAGE_DISPLAYED_SYNCHRONIZATION;
        }

        public boolean mdsServerAssist() {
            return hasDiscoFeature(account.getJid().asBareJid(), Namespace.MDS_DISPLAYED);
        }
    }
}
