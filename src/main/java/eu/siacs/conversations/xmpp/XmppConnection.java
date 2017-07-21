package eu.siacs.conversations.xmpp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.ConnectException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.DomainHostnameVerifier;
import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.XmppDomainVerifier;
import eu.siacs.conversations.crypto.sasl.Anonymous;
import eu.siacs.conversations.crypto.sasl.DigestMd5;
import eu.siacs.conversations.crypto.sasl.External;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import eu.siacs.conversations.crypto.sasl.ScramSha1;
import eu.siacs.conversations.crypto.sasl.ScramSha256;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.IP;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.Resolver;
import eu.siacs.conversations.utils.SSLSocketHelper;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.AbstractAcknowledgeableStanza;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.csi.ActivePacket;
import eu.siacs.conversations.xmpp.stanzas.csi.InactivePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.AckPacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.EnablePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.RequestPacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.ResumePacket;

public class XmppConnection implements Runnable {

	private static final int PACKET_IQ = 0;
	private static final int PACKET_MESSAGE = 1;
	private static final int PACKET_PRESENCE = 2;
	protected final Account account;
	private final WakeLock wakeLock;
	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter = new TagWriter();
	private final Features features = new Features(this);
	private boolean needsBinding = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	private final HashMap<Jid, ServiceDiscoveryResult> disco = new HashMap<>();

	private String streamId = null;
	private int smVersion = 3;
	private final SparseArray<AbstractAcknowledgeableStanza> mStanzaQueue = new SparseArray<>();

	private int stanzasReceived = 0;
	private int stanzasSent = 0;
	private long lastPacketReceived = 0;
	private long lastPingSent = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private long lastDiscoStarted = 0;
	private AtomicInteger mPendingServiceDiscoveries = new AtomicInteger(0);
	private AtomicBoolean mWaitForDisco = new AtomicBoolean(true);
	private AtomicBoolean mWaitingForSmCatchup = new AtomicBoolean(false);
	private AtomicInteger mSmCatchupMessageCounter = new AtomicInteger(0);
	private boolean mInteractive = false;
	private int attempt = 0;
	private final Hashtable<String, Pair<IqPacket, OnIqPacketReceived>> packetCallbacks = new Hashtable<>();
	private OnPresencePacketReceived presenceListener = null;
	private OnJinglePacketReceived jingleListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;
	private OnMessagePacketReceived messageListener = null;
	private OnStatusChanged statusListener = null;
	private OnBindListener bindListener = null;
	private final ArrayList<OnAdvancedStreamFeaturesLoaded> advancedStreamFeaturesLoadedListeners = new ArrayList<>();
	private OnMessageAcknowledged acknowledgedListener = null;
	private final XmppConnectionService mXmppConnectionService;

	private SaslMechanism saslMechanism;
	private String webRegistrationUrl = null;
	private String verifiedHostname = null;

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
			Log.d(Config.LOGTAG,"getting certificate chain");
			try {
				return KeyChain.getCertificateChain(mXmppConnectionService, alias);
			} catch (Exception e) {
				Log.d(Config.LOGTAG,e.getMessage());
				return new X509Certificate[0];
			}
		}

		@Override
		public String[] getClientAliases(String s, Principal[] principals) {
			final String alias = account.getPrivateKeyAlias();
			return alias != null ? new String[]{alias} : new String[0];
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

	public final OnIqPacketReceived registrationResponseListener =  new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				account.setOption(Account.OPTION_REGISTER, false);
				forceCloseSocket();
				changeStatus(Account.State.REGISTRATION_SUCCESSFUL);
			} else {
				final List<String> PASSWORD_TOO_WEAK_MSGS = Arrays.asList(
						"The password is too weak",
						"Please use a longer password.");
				Element error = packet.findChild("error");
				Account.State state = Account.State.REGISTRATION_FAILED;
				if (error != null) {
					if (error.hasChild("conflict")) {
						state = Account.State.REGISTRATION_CONFLICT;
					} else if (error.hasChild("resource-constraint")
							&& "wait".equals(error.getAttribute("type"))) {
						state = Account.State.REGISTRATION_PLEASE_WAIT;
					} else if (error.hasChild("not-acceptable")
							&& PASSWORD_TOO_WEAK_MSGS.contains(error.findChildContent("text"))) {
						state = Account.State.REGISTRATION_PASSWORD_TOO_WEAK;
					}
				}
				changeStatus(state);
				forceCloseSocket();
			}
		}
	};

	public XmppConnection(final Account account, final XmppConnectionService service) {
		this.account = account;
		this.wakeLock = service.getPowerManager().newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, account.getJid().toBareJid().toString());
		mXmppConnectionService = service;
	}

	protected void changeStatus(final Account.State nextStatus) {
		synchronized (this) {
			if (Thread.currentThread().isInterrupted()) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": not changing status to " + nextStatus + " because thread was interrupted");
				return;
			}
			if (account.getStatus() != nextStatus) {
				if ((nextStatus == Account.State.OFFLINE)
						&& (account.getStatus() != Account.State.CONNECTING)
						&& (account.getStatus() != Account.State.ONLINE)
						&& (account.getStatus() != Account.State.DISABLED)) {
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
		Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": connecting");
		features.encryptionEnabled = false;
		this.attempt++;
		this.verifiedHostname = null; //will be set if user entered hostname is being used or hostname was verified with dnssec
		try {
			Socket localSocket;
			shouldAuthenticate = needsBinding = !account.isOptionSet(Account.OPTION_REGISTER);
			this.changeStatus(Account.State.CONNECTING);
			final boolean useTor = mXmppConnectionService.useTorToConnect() || account.isOnion();
			final boolean extended = mXmppConnectionService.showExtendedConnectionOptions();
			if (useTor) {
				String destination;
				if (account.getHostname().isEmpty()) {
					destination = account.getServer().toString();
				} else {
					destination = account.getHostname();
					this.verifiedHostname = destination;
				}
				Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": connect to " + destination + " via Tor");
				localSocket = SocksSocketFactory.createSocketOverTor(destination, account.getPort());
				try {
					startXmpp(localSocket);
				} catch (InterruptedException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": thread was interrupted before beginning stream");
					return;
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
			} else if (extended && !account.getHostname().isEmpty()) {

				this.verifiedHostname = account.getHostname();

				try {
					InetSocketAddress address = new InetSocketAddress(this.verifiedHostname, account.getPort());
					features.encryptionEnabled = address.getPort() == 5223;
					if (features.encryptionEnabled) {
						try {
							final TlsFactoryVerifier tlsFactoryVerifier = getTlsFactoryVerifier();
							localSocket = tlsFactoryVerifier.factory.createSocket();
							localSocket.connect(address, Config.SOCKET_TIMEOUT * 1000);
							final SSLSession session = ((SSLSocket) localSocket).getSession();
							final String domain = account.getJid().getDomainpart();
							if (!tlsFactoryVerifier.verifier.verify(domain, this.verifiedHostname, session)) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": TLS certificate verification failed");
								throw new StateChangingException(Account.State.TLS_ERROR);
							}
						} catch (KeyManagementException e) {
							throw new StateChangingException(Account.State.TLS_ERROR);
						}
					} else {
						localSocket = new Socket();
						localSocket.connect(address, Config.SOCKET_TIMEOUT * 1000);
					}
				} catch (IOException | IllegalArgumentException e) {
					throw new UnknownHostException();
				}
				try {
					startXmpp(localSocket);
				} catch (InterruptedException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": thread was interrupted before beginning stream");
					return;
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
			} else if (IP.matches(account.getServer().toString())) {
				localSocket = new Socket();
				try {
					localSocket.connect(new InetSocketAddress(account.getServer().toString(), 5222), Config.SOCKET_TIMEOUT * 1000);
				} catch (IOException e) {
					throw new UnknownHostException();
				}
				try {
					startXmpp(localSocket);
				} catch (InterruptedException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": thread was interrupted before beginning stream");
					return;
				} catch (Exception e) {
					throw new IOException(e.getMessage());
				}
			} else {
				List<Resolver.Result> results = Resolver.resolve(account.getJid().getDomainpart());
				for (Iterator<Resolver.Result> iterator = results.iterator(); iterator.hasNext(); ) {
					final Resolver.Result result = iterator.next();
					if (Thread.currentThread().isInterrupted()) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": Thread was interrupted");
						return;
					}
					try {
						// if tls is true, encryption is implied and must not be started
						features.encryptionEnabled = result.isDirectTls();
						verifiedHostname = result.isAuthenticated() ? result.getHostname().toString() : null;
						final InetSocketAddress addr;
						if (result.getIp() != null) {
							addr = new InetSocketAddress(result.getIp(), result.getPort());
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns " + result.getHostname().toString()
									+ "/" + result.getIp().getHostAddress() + ":" + result.getPort() + " tls: " + features.encryptionEnabled);
						} else {
							addr = new InetSocketAddress(IDN.toASCII(result.getHostname().toString()), result.getPort());
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns "
									+ result.getHostname().toString() + ":" + result.getPort() + " tls: " + features.encryptionEnabled);
						}

						if (!features.encryptionEnabled) {
							localSocket = new Socket();
							localSocket.connect(addr, Config.SOCKET_TIMEOUT * 1000);
						} else {
							final TlsFactoryVerifier tlsFactoryVerifier = getTlsFactoryVerifier();
							localSocket = tlsFactoryVerifier.factory.createSocket();

							if (localSocket == null) {
								throw new IOException("could not initialize ssl socket");
							}

							SSLSocketHelper.setSecurity((SSLSocket) localSocket);
							SSLSocketHelper.setSNIHost(tlsFactoryVerifier.factory, (SSLSocket) localSocket, account.getServer().getDomainpart());
							SSLSocketHelper.setAlpnProtocol(tlsFactoryVerifier.factory, (SSLSocket) localSocket, "xmpp-client");

							localSocket.connect(addr, Config.SOCKET_TIMEOUT * 1000);

							if (!tlsFactoryVerifier.verifier.verify(account.getServer().getDomainpart(), verifiedHostname, ((SSLSocket) localSocket).getSession())) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": TLS certificate verification failed");
								if (!iterator.hasNext()) {
									throw new StateChangingException(Account.State.TLS_ERROR);
								}
							}
						}
						if (startXmpp(localSocket)) {
							break; // successfully connected to server that speaks xmpp
						} else {
							localSocket.close();
						}
					} catch (final StateChangingException e) {
						throw e;
					} catch (InterruptedException e) {
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": thread was interrupted before beginning stream");
						return;
					} catch (final Throwable e) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage() + "(" + e.getClass().getName() + ")");
						if (!iterator.hasNext()) {
							throw new UnknownHostException();
						}
					}
				}
			}
			processStream();
		}  catch (final SecurityException e) {
			this.changeStatus(Account.State.MISSING_INTERNET_PERMISSION);
		} catch(final StateChangingException e) {
			this.changeStatus(e.state);
		} catch (final UnknownHostException | ConnectException e) {
			this.changeStatus(Account.State.SERVER_NOT_FOUND);
		} catch (final SocksSocketFactory.SocksProxyNotFoundException e) {
			this.changeStatus(Account.State.TOR_NOT_AVAILABLE);
		} catch (final IOException | XmlPullParserException | NoSuchAlgorithmException e) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage());
			this.changeStatus(Account.State.OFFLINE);
			this.attempt = Math.max(0, this.attempt - 1);
		} finally {
			if (!Thread.currentThread().isInterrupted()) {
				forceCloseSocket();
				if (wakeLock.isHeld()) {
					try {
						wakeLock.release();
					} catch (final RuntimeException ignored) {
					}
				}
			} else {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": not force closing socket and releasing wake lock (is held="+wakeLock.isHeld()+") because thread was interrupted");
			}
		}
	}

	/**
	 * Starts xmpp protocol, call after connecting to socket
	 * @return true if server returns with valid xmpp, false otherwise
     */
	private boolean startXmpp(Socket socket) throws Exception {
		if (Thread.currentThread().isInterrupted()) {
			throw new InterruptedException();
		}
		this.socket = socket;
		tagReader = new XmlReader(wakeLock);
		if (tagWriter != null) {
			tagWriter.forceClose();
		}
		tagWriter = new TagWriter();
		tagWriter.setOutputStream(socket.getOutputStream());
		tagReader.setInputStream(socket.getInputStream());
		tagWriter.beginDocument();
		sendStartStream();
		final Tag tag = tagReader.readTag();
		return tag != null && tag.isStart("stream");
	}

	private static class TlsFactoryVerifier {
		private final SSLSocketFactory factory;
		private final DomainHostnameVerifier verifier;

		public TlsFactoryVerifier(final SSLSocketFactory factory, final DomainHostnameVerifier verifier) throws IOException {
			this.factory = factory;
			this.verifier = verifier;
			if (factory == null || verifier == null) {
				throw new IOException("could not setup ssl");
			}
		}
	}

	private TlsFactoryVerifier getTlsFactoryVerifier() throws NoSuchAlgorithmException, KeyManagementException, IOException {
		final SSLContext sc = SSLSocketHelper.getSSLContext();
		MemorizingTrustManager trustManager = this.mXmppConnectionService.getMemorizingTrustManager();
		KeyManager[] keyManager;
		if (account.getPrivateKeyAlias() != null && account.getPassword().isEmpty()) {
			keyManager = new KeyManager[]{new MyKeyManager()};
		} else {
			keyManager = null;
		}
		String domain = account.getJid().getDomainpart();
		sc.init(keyManager, new X509TrustManager[]{mInteractive ? trustManager.getInteractive(domain) : trustManager.getNonInteractive(domain)}, mXmppConnectionService.getRNG());
		final SSLSocketFactory factory = sc.getSocketFactory();
		final DomainHostnameVerifier verifier = trustManager.wrapHostnameVerifier(new XmppDomainVerifier(), mInteractive);
		return new TlsFactoryVerifier(factory, verifier);
	}

	@Override
	public void run() {
		synchronized (this) {
			if (Thread.currentThread().isInterrupted()) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": aborting connect because thread was interrupted");
				return;
			}
			forceCloseSocket();
		}
		connect();
	}

	private void processStream() throws XmlPullParserException, IOException, NoSuchAlgorithmException {
		Tag nextTag = tagReader.readTag();
		while (nextTag != null && !nextTag.isEnd("stream")) {
			if (nextTag.isStart("error")) {
				processStreamError(nextTag);
			} else if (nextTag.isStart("features")) {
				processStreamFeatures(nextTag);
			} else if (nextTag.isStart("proceed")) {
				switchOverToTls(nextTag);
			} else if (nextTag.isStart("success")) {
				final String challenge = tagReader.readElement(nextTag).getContent();
				try {
					saslMechanism.getResponse(challenge);
				} catch (final SaslMechanism.AuthenticationException e) {
					Log.e(Config.LOGTAG, String.valueOf(e));
					throw new StateChangingException(Account.State.UNAUTHORIZED);
				}
				Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": logged in");
				account.setKey(Account.PINNED_MECHANISM_KEY,
						String.valueOf(saslMechanism.getPriority()));
				tagReader.reset();
				sendStartStream();
				final Tag tag = tagReader.readTag();
				if (tag != null && tag.isStart("stream")) {
					processStream();
				} else {
					throw new IOException("server didn't restart stream after successful auth");
				}
				break;
			} else if (nextTag.isStart("failure")) {
				final Element failure = tagReader.readElement(nextTag);
				if (Namespace.SASL.equals(failure.getNamespace())) {
					final String text = failure.findChildContent("text");
					if (failure.hasChild("account-disabled")
							&& text != null
							&& text.contains("renew")
							&& Config.MAGIC_CREATE_DOMAIN != null
							&& text.contains(Config.MAGIC_CREATE_DOMAIN)) {
						throw new StateChangingException(Account.State.PAYMENT_REQUIRED);
					} else {
						throw new StateChangingException(Account.State.UNAUTHORIZED);
					}
				} else if (Namespace.TLS.equals(failure.getNamespace())) {
					throw new StateChangingException(Account.State.TLS_ERROR);
				} else {
					throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
				}
			} else if (nextTag.isStart("challenge")) {
				final String challenge = tagReader.readElement(nextTag).getContent();
				final Element response = new Element("response",Namespace.SASL);
				try {
					response.setContent(saslMechanism.getResponse(challenge));
				} catch (final SaslMechanism.AuthenticationException e) {
					// TODO: Send auth abort tag.
					Log.e(Config.LOGTAG, e.toString());
				}
				tagWriter.writeElement(response);
			} else if (nextTag.isStart("enabled")) {
				final Element enabled = tagReader.readElement(nextTag);
				if ("true".equals(enabled.getAttribute("resume"))) {
					this.streamId = enabled.getAttribute("id");
					Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
							+ ": stream management(" + smVersion
							+ ") enabled (resumable)");
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
							+ ": stream management(" + smVersion + ") enabled");
				}
				this.stanzasReceived = 0;
				final RequestPacket r = new RequestPacket(smVersion);
				tagWriter.writeStanzaAsync(r);
			} else if (nextTag.isStart("resumed")) {
				this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
				lastPacketReceived = SystemClock.elapsedRealtime();
				final Element resumed = tagReader.readElement(nextTag);
				final String h = resumed.getAttribute("h");
				try {
					ArrayList<AbstractAcknowledgeableStanza> failedStanzas = new ArrayList<>();
					synchronized (this.mStanzaQueue) {
						final int serverCount = Integer.parseInt(h);
						if (serverCount != stanzasSent) {
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": session resumed with lost packages");
							stanzasSent = serverCount;
						} else {
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": session resumed");
						}
						acknowledgeStanzaUpTo(serverCount);
						for (int i = 0; i < this.mStanzaQueue.size(); ++i) {
							failedStanzas.add(mStanzaQueue.valueAt(i));
						}
						mStanzaQueue.clear();
					}
					Log.d(Config.LOGTAG, "resending " + failedStanzas.size() + " stanzas");
					for (AbstractAcknowledgeableStanza packet : failedStanzas) {
						if (packet instanceof MessagePacket) {
							MessagePacket message = (MessagePacket) packet;
							mXmppConnectionService.markMessage(account,
									message.getTo().toBareJid(),
									message.getId(),
									Message.STATUS_UNSEND);
						}
						sendPacket(packet);
					}
				} catch (final NumberFormatException ignored) {
				}
				Log.d(Config.LOGTAG, account.getJid().toBareJid()+ ": online with resource " + account.getResource());
				changeStatus(Account.State.ONLINE);
			} else if (nextTag.isStart("r")) {
				tagReader.readElement(nextTag);
				if (Config.EXTENDED_SM_LOGGING) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": acknowledging stanza #" + this.stanzasReceived);
				}
				final AckPacket ack = new AckPacket(this.stanzasReceived, smVersion);
				tagWriter.writeStanzaAsync(ack);
			} else if (nextTag.isStart("a")) {
				boolean accountUiNeedsRefresh = false;
				synchronized (NotificationService.CATCHUP_LOCK) {
					if (mWaitingForSmCatchup.compareAndSet(true, false)) {
						int count = mSmCatchupMessageCounter.get();
						Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": SM catchup complete (" + count + ")");
						accountUiNeedsRefresh = true;
						if (count > 0) {
							mXmppConnectionService.getNotificationService().finishBacklog(true, account);
						}
					}
				}
				if (accountUiNeedsRefresh) {
					mXmppConnectionService.updateAccountUi();
				}
				final Element ack = tagReader.readElement(nextTag);
				lastPacketReceived = SystemClock.elapsedRealtime();
				try {
					synchronized (this.mStanzaQueue) {
						final int serverSequence = Integer.parseInt(ack.getAttribute("h"));
						acknowledgeStanzaUpTo(serverSequence);
					}
				} catch (NumberFormatException | NullPointerException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": server send ack without sequence number");
				}
			} else if (nextTag.isStart("failed")) {
				Element failed = tagReader.readElement(nextTag);
				try {
					final int serverCount = Integer.parseInt(failed.getAttribute("h"));
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": resumption failed but server acknowledged stanza #"+serverCount);
					synchronized (this.mStanzaQueue) {
						acknowledgeStanzaUpTo(serverCount);
					}
				} catch (NumberFormatException | NullPointerException e) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": resumption failed");
				}
				resetStreamId();
				sendBindRequest();
			} else if (nextTag.isStart("iq")) {
				processIq(nextTag);
			} else if (nextTag.isStart("message")) {
				processMessage(nextTag);
			} else if (nextTag.isStart("presence")) {
				processPresence(nextTag);
			}
			nextTag = tagReader.readTag();
		}
	}

	private void acknowledgeStanzaUpTo(int serverCount) {
		for (int i = 0; i < mStanzaQueue.size(); ++i) {
			if (serverCount >= mStanzaQueue.keyAt(i)) {
				if (Config.EXTENDED_SM_LOGGING) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": server acknowledged stanza #" + mStanzaQueue.keyAt(i));
				}
				AbstractAcknowledgeableStanza stanza = mStanzaQueue.valueAt(i);
				if (stanza instanceof MessagePacket && acknowledgedListener != null) {
					MessagePacket packet = (MessagePacket) stanza;
					acknowledgedListener.onMessageAcknowledged(account, packet.getId());
				}
				mStanzaQueue.removeAt(i);
				i--;
			}
		}
	}

	private Element processPacket(final Tag currentTag, final int packetType)
		throws XmlPullParserException, IOException {
		Element element;
		switch (packetType) {
			case PACKET_IQ:
				element = new IqPacket();
				break;
			case PACKET_MESSAGE:
				element = new MessagePacket();
				break;
			case PACKET_PRESENCE:
				element = new PresencePacket();
				break;
			default:
				return null;
		}
		element.setAttributes(currentTag.getAttributes());
		Tag nextTag = tagReader.readTag();
		if (nextTag == null) {
			throw new IOException("interrupted mid tag");
		}
		while (!nextTag.isEnd(element.getName())) {
			if (!nextTag.isNo()) {
				final Element child = tagReader.readElement(nextTag);
				final String type = currentTag.getAttribute("type");
				if (packetType == PACKET_IQ
						&& "jingle".equals(child.getName())
						&& ("set".equalsIgnoreCase(type) || "get"
							.equalsIgnoreCase(type))) {
					element = new JinglePacket();
					element.setAttributes(currentTag.getAttributes());
							}
				element.addChild(child);
			}
			nextTag = tagReader.readTag();
			if (nextTag == null) {
				throw new IOException("interrupted mid tag");
			}
		}
		if (stanzasReceived == Integer.MAX_VALUE) {
			resetStreamId();
			throw new IOException("time to restart the session. cant handle >2 billion pcks");
		}
		++stanzasReceived;
		lastPacketReceived = SystemClock.elapsedRealtime();
		if (Config.BACKGROUND_STANZA_LOGGING && mXmppConnectionService.checkListeners()) {
			Log.d(Config.LOGTAG,"[background stanza] "+element);
		}
		return element;
	}

	private void processIq(final Tag currentTag) throws XmlPullParserException, IOException {
		final IqPacket packet = (IqPacket) processPacket(currentTag, PACKET_IQ);

		if (packet.getId() == null) {
			return; // an iq packet without id is definitely invalid
		}

		if (packet instanceof JinglePacket) {
			if (this.jingleListener != null) {
				this.jingleListener.onJinglePacketReceived(account,(JinglePacket) packet);
			}
		} else {
			OnIqPacketReceived callback = null;
			synchronized (this.packetCallbacks) {
				if (packetCallbacks.containsKey(packet.getId())) {
					final Pair<IqPacket, OnIqPacketReceived> packetCallbackDuple = packetCallbacks.get(packet.getId());
					// Packets to the server should have responses from the server
					if (packetCallbackDuple.first.toServer(account)) {
						if (packet.fromServer(account)) {
							callback = packetCallbackDuple.second;
							packetCallbacks.remove(packet.getId());
						} else {
							Log.e(Config.LOGTAG, account.getJid().toBareJid().toString() + ": ignoring spoofed iq packet");
						}
					} else {
						if (packet.getFrom().equals(packetCallbackDuple.first.getTo())) {
							callback = packetCallbackDuple.second;
							packetCallbacks.remove(packet.getId());
						} else {
							Log.e(Config.LOGTAG, account.getJid().toBareJid().toString() + ": ignoring spoofed iq packet");
						}
					}
				} else if (packet.getType() == IqPacket.TYPE.GET || packet.getType() == IqPacket.TYPE.SET) {
					callback = this.unregisteredIqListener;
				}
			}
			if (callback != null) {
				try {
					callback.onIqPacketReceived(account, packet);
				} catch (StateChangingError error) {
					throw new StateChangingException(error.state);
				}
			}
		}
	}

	private void processMessage(final Tag currentTag) throws XmlPullParserException, IOException {
		final MessagePacket packet = (MessagePacket) processPacket(currentTag,PACKET_MESSAGE);
		this.messageListener.onMessagePacketReceived(account, packet);
	}

	private void processPresence(final Tag currentTag) throws XmlPullParserException, IOException {
		PresencePacket packet = (PresencePacket) processPacket(currentTag, PACKET_PRESENCE);
		this.presenceListener.onPresencePacketReceived(account, packet);
	}

	private void sendStartTLS() throws IOException {
		final Tag startTLS = Tag.empty("starttls");
		startTLS.setAttribute("xmlns", Namespace.TLS);
		tagWriter.writeTag(startTLS);
	}



	private void switchOverToTls(final Tag currentTag) throws XmlPullParserException, IOException {
		tagReader.readTag();
		try {
			final TlsFactoryVerifier tlsFactoryVerifier = getTlsFactoryVerifier();
			final InetAddress address = socket == null ? null : socket.getInetAddress();

			if (address == null) {
				throw new IOException("could not setup ssl");
			}

			final SSLSocket sslSocket = (SSLSocket) tlsFactoryVerifier.factory.createSocket(socket, address.getHostAddress(), socket.getPort(), true);

			if (sslSocket == null) {
				throw new IOException("could not initialize ssl socket");
			}

			SSLSocketHelper.setSecurity(sslSocket);

			if (!tlsFactoryVerifier.verifier.verify(account.getServer().getDomainpart(), this.verifiedHostname, sslSocket.getSession())) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": TLS certificate verification failed");
				throw new StateChangingException(Account.State.TLS_ERROR);
			}
			tagReader.setInputStream(sslSocket.getInputStream());
			tagWriter.setOutputStream(sslSocket.getOutputStream());
			sendStartStream();
			Log.d(Config.LOGTAG, account.getJid().toBareJid()+ ": TLS connection established");
			features.encryptionEnabled = true;
			final Tag tag = tagReader.readTag();
			if (tag != null && tag.isStart("stream")) {
				processStream();
			} else {
				throw new IOException("server didn't restart stream after STARTTLS");
			}
			sslSocket.close();
		} catch (final NoSuchAlgorithmException | KeyManagementException e1) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": TLS certificate verification failed");
			throw new StateChangingException(Account.State.TLS_ERROR);
		}
	}

	private void processStreamFeatures(final Tag currentTag)
		throws XmlPullParserException, IOException {
		this.streamFeatures = tagReader.readElement(currentTag);
		if (this.streamFeatures.hasChild("starttls") && !features.encryptionEnabled) {
			sendStartTLS();
		} else if (this.streamFeatures.hasChild("register") && account.isOptionSet(Account.OPTION_REGISTER)) {
			if (features.encryptionEnabled || Config.ALLOW_NON_TLS_CONNECTIONS) {
				sendRegistryRequest();
			} else {
				throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
			}
		} else if (!this.streamFeatures.hasChild("register") && account.isOptionSet(Account.OPTION_REGISTER)) {
			throw new StateChangingException(Account.State.REGISTRATION_NOT_SUPPORTED);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate
				&& (features.encryptionEnabled || Config.ALLOW_NON_TLS_CONNECTIONS)) {
			authenticate();
		} else if (this.streamFeatures.hasChild("sm", "urn:xmpp:sm:" + smVersion) && streamId != null) {
			if (Config.EXTENDED_SM_LOGGING) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": resuming after stanza #"+stanzasReceived);
			}
			final ResumePacket resume = new ResumePacket(this.streamId, stanzasReceived, smVersion);
			this.mSmCatchupMessageCounter.set(0);
			this.mWaitingForSmCatchup.set(true);
			this.tagWriter.writeStanzaAsync(resume);
		} else if (needsBinding) {
			if (this.streamFeatures.hasChild("bind")) {
				sendBindRequest();
			} else {
				throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
			}
		}
	}

	private void authenticate() throws IOException {
		final List<String> mechanisms = extractMechanisms(streamFeatures
				.findChild("mechanisms"));
		final Element auth = new Element("auth",Namespace.SASL);
		if (mechanisms.contains("EXTERNAL") && account.getPrivateKeyAlias() != null) {
			saslMechanism = new External(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("SCRAM-SHA-256")) {
			saslMechanism = new ScramSha256(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("SCRAM-SHA-1")) {
			saslMechanism = new ScramSha1(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("PLAIN")) {
			saslMechanism = new Plain(tagWriter, account);
		} else if (mechanisms.contains("DIGEST-MD5")) {
			saslMechanism = new DigestMd5(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("ANONYMOUS")) {
			saslMechanism = new Anonymous(tagWriter, account, mXmppConnectionService.getRNG());
		}
		if (saslMechanism != null) {
			final int pinnedMechanism = account.getKeyAsInt(Account.PINNED_MECHANISM_KEY, -1);
			if (pinnedMechanism > saslMechanism.getPriority()) {
				Log.e(Config.LOGTAG, "Auth failed. Authentication mechanism " + saslMechanism.getMechanism() +
						" has lower priority (" + String.valueOf(saslMechanism.getPriority()) +
						") than pinned priority (" + pinnedMechanism +
						"). Possible downgrade attack?");
				throw new StateChangingException(Account.State.DOWNGRADE_ATTACK);
			}
			Log.d(Config.LOGTAG, account.getJid().toString() + ": Authenticating with " + saslMechanism.getMechanism());
			auth.setAttribute("mechanism", saslMechanism.getMechanism());
			if (!saslMechanism.getClientFirstMessage().isEmpty()) {
				auth.setContent(saslMechanism.getClientFirstMessage());
			}
			tagWriter.writeElement(auth);
		} else {
			throw new StateChangingException(Account.State.INCOMPATIBLE_SERVER);
		}
	}

	private List<String> extractMechanisms(final Element stream) {
		final ArrayList<String> mechanisms = new ArrayList<>(stream
				.getChildren().size());
		for (final Element child : stream.getChildren()) {
			mechanisms.add(child.getContent());
		}
		return mechanisms;
	}

	private void sendRegistryRequest() {
		final IqPacket register = new IqPacket(IqPacket.TYPE.GET);
		register.query("jabber:iq:register");
		register.setTo(account.getServer());
		sendUnmodifiedIqPacket(register, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				boolean failed = false;
				if (packet.getType() == IqPacket.TYPE.RESULT
						&& packet.query().hasChild("username")
						&& (packet.query().hasChild("password"))) {
					final IqPacket register = new IqPacket(IqPacket.TYPE.SET);
					final Element username = new Element("username").setContent(account.getUsername());
					final Element password = new Element("password").setContent(account.getPassword());
					register.query("jabber:iq:register").addChild(username);
					register.query().addChild(password);
					register.setFrom(account.getJid().toBareJid());
					sendUnmodifiedIqPacket(register, registrationResponseListener);
				} else if (packet.getType() == IqPacket.TYPE.RESULT
						&& (packet.query().hasChild("x", "jabber:x:data"))) {
					final Data data = Data.parse(packet.query().findChild("x", "jabber:x:data"));
					final Element blob = packet.query().findChild("data", "urn:xmpp:bob");
					final String id = packet.getId();

					Bitmap captcha = null;
					if (blob != null) {
						try {
							final String base64Blob = blob.getContent();
							final byte[] strBlob = Base64.decode(base64Blob, Base64.DEFAULT);
							InputStream stream = new ByteArrayInputStream(strBlob);
							captcha = BitmapFactory.decodeStream(stream);
						} catch (Exception e) {
							//ignored
						}
					} else {
						try {
							Field url = data.getFieldByName("url");
							String urlString = url.findChildContent("value");
							URL uri = new URL(urlString);
							captcha = BitmapFactory.decodeStream(uri.openConnection().getInputStream());
						} catch (IOException e) {
							Log.e(Config.LOGTAG, e.toString());
						}
					}

					if (captcha != null) {
						failed = !mXmppConnectionService.displayCaptchaRequest(account, id, data, captcha);
					}
				} else {
					failed = true;
				}

				if (failed) {
					final Element query = packet.query();
					final String instructions = query.findChildContent("instructions");
					final Element oob = query.findChild("x",Namespace.OOB);
					final String url = oob == null ? null : oob.findChildContent("url");
					if (url == null && instructions != null) {
						Matcher matcher = Patterns.AUTOLINK_WEB_URL.matcher(instructions);
						if (matcher.find()) {
							setAccountCreationFailed(instructions.substring(matcher.start(),matcher.end()));
						} else {
							setAccountCreationFailed(null);
						}
					} else {
						setAccountCreationFailed(url);
					}
				}
			}
		});
	}

	private void setAccountCreationFailed(String url) {
		if (url != null && (url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("https://"))) {
			changeStatus(Account.State.REGISTRATION_WEB);
			this.webRegistrationUrl = url;
		} else {
			changeStatus(Account.State.REGISTRATION_FAILED);
		}
		disconnect(true);
		Log.d(Config.LOGTAG, account.getJid().toBareJid()+": could not register. url="+url);
	}

	public String getWebRegistrationUrl() {
		return this.webRegistrationUrl;
	}

	public void resetEverything() {
		resetAttemptCount(true);
		resetStreamId();
		clearIqCallbacks();
		mStanzaQueue.clear();
		this.webRegistrationUrl = null;
		synchronized (this.disco) {
			disco.clear();
		}
	}

	private void sendBindRequest() {
		while(!mXmppConnectionService.areMessagesInitialized() && socket != null && !socket.isClosed()) {
			try {
				Thread.sleep(500);
			} catch (final InterruptedException ignored) {
			}
		}
		needsBinding = false;
		clearIqCallbacks();
		final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")
				.addChild("resource").setContent(account.getResource());
		this.sendUnmodifiedIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
					return;
				}
				final Element bind = packet.findChild("bind");
				if (bind != null && packet.getType() == IqPacket.TYPE.RESULT) {
					final Element jid = bind.findChild("jid");
					if (jid != null && jid.getContent() != null) {
						try {
							if (account.setJid(Jid.fromString(jid.getContent()))) {
								Log.d(Config.LOGTAG,account.getJid().toBareJid()+": bare jid changed during bind. updating database");
								mXmppConnectionService.databaseBackend.updateAccount(account);
							}
							if (streamFeatures.hasChild("session")
									&& !streamFeatures.findChild("session").hasChild("optional")) {
								sendStartSession();
							} else {
								sendPostBindInitialization();
							}
							return;
						} catch (final InvalidJidException e) {
							Log.d(Config.LOGTAG,account.getJid().toBareJid()+": server reported invalid jid ("+jid.getContent()+") on bind");
						}
					} else {
						Log.d(Config.LOGTAG, account.getJid() + ": disconnecting because of bind failure. (no jid)");
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid() + ": disconnecting because of bind failure (" + packet.toString());
				}
				final Element error = packet.findChild("error");
				final String resource = account.getResource().split("\\.")[0];
				if (packet.getType() == IqPacket.TYPE.ERROR && error != null && error.hasChild("conflict")) {
					account.setResource(resource + "." + nextRandomId());
				} else {
					account.setResource(resource);
				}
				throw new StateChangingError(Account.State.BIND_FAILURE);
			}
		});
	}

	private void clearIqCallbacks() {
		final IqPacket failurePacket = new IqPacket(IqPacket.TYPE.TIMEOUT);
		final ArrayList<OnIqPacketReceived> callbacks = new ArrayList<>();
		synchronized (this.packetCallbacks) {
			if (this.packetCallbacks.size() == 0) {
				return;
			}
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": clearing "+this.packetCallbacks.size()+" iq callbacks");
			final Iterator<Pair<IqPacket, OnIqPacketReceived>> iterator = this.packetCallbacks.values().iterator();
			while (iterator.hasNext()) {
				Pair<IqPacket, OnIqPacketReceived> entry = iterator.next();
				callbacks.add(entry.second);
				iterator.remove();
			}
		}
		for(OnIqPacketReceived callback : callbacks) {
			try {
				callback.onIqPacketReceived(account, failurePacket);
			} catch (StateChangingError error) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": caught StateChangingError("+error.state.toString()+") while clearing callbacks");
				//ignore
			}
		}
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": done clearing iq callbacks. " + this.packetCallbacks.size() + " left");
	}

	public void sendDiscoTimeout() {
		if (mWaitForDisco.compareAndSet(true, false)) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": finalizing bind after disco timeout");
			finalizeBind();
		}
	}

	private void sendStartSession() {
		Log.d(Config.LOGTAG,account.getJid().toBareJid()+": sending legacy session to outdated server");
		final IqPacket startSession = new IqPacket(IqPacket.TYPE.SET);
		startSession.addChild("session", "urn:ietf:params:xml:ns:xmpp-session");
		this.sendUnmodifiedIqPacket(startSession, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					sendPostBindInitialization();
				} else if (packet.getType() != IqPacket.TYPE.TIMEOUT) {
					throw new StateChangingError(Account.State.SESSION_FAILURE);
				}
			}
		});
	}

	private void sendPostBindInitialization() {
		smVersion = 0;
		if (streamFeatures.hasChild("sm", "urn:xmpp:sm:3")) {
			smVersion = 3;
		} else if (streamFeatures.hasChild("sm", "urn:xmpp:sm:2")) {
			smVersion = 2;
		}
		if (smVersion != 0) {
			synchronized (this.mStanzaQueue) {
				final EnablePacket enable = new EnablePacket(smVersion);
				tagWriter.writeStanzaAsync(enable);
				stanzasSent = 0;
				mStanzaQueue.clear();
			}
		}
		features.carbonsEnabled = false;
		features.blockListRequested = false;
		synchronized (this.disco) {
			this.disco.clear();
		}
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": starting service discovery");
		mPendingServiceDiscoveries.set(0);
		if (smVersion == 0 || Patches.DISCO_EXCEPTIONS.contains(account.getJid().getDomainpart())) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": do not wait for service discovery");
			mWaitForDisco.set(false);
		} else {
			mWaitForDisco.set(true);
		}
		lastDiscoStarted = SystemClock.elapsedRealtime();
		mXmppConnectionService.scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
		Element caps = streamFeatures.findChild("c");
		final String hash = caps == null ? null : caps.getAttribute("hash");
		final String ver = caps == null ? null : caps.getAttribute("ver");
		ServiceDiscoveryResult discoveryResult = null;
		if (hash != null && ver != null) {
			discoveryResult = mXmppConnectionService.getCachedServiceDiscoveryResult(new Pair<>(hash, ver));
		}
		if (discoveryResult == null) {
			sendServiceDiscoveryInfo(account.getServer());
		} else {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": server caps came from cache");
			disco.put(account.getServer(), discoveryResult);
		}
		sendServiceDiscoveryInfo(account.getJid().toBareJid());
		sendServiceDiscoveryItems(account.getServer());

		if (!mWaitForDisco.get()) {
			finalizeBind();
		}
		this.lastSessionStarted = SystemClock.elapsedRealtime();
	}

	private void sendServiceDiscoveryInfo(final Jid jid) {
		mPendingServiceDiscoveries.incrementAndGet();
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.setTo(jid);
		iq.query("http://jabber.org/protocol/disco#info");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					boolean advancedStreamFeaturesLoaded;
					synchronized (XmppConnection.this.disco) {
						ServiceDiscoveryResult result = new ServiceDiscoveryResult(packet);
						if (jid.equals(account.getServer())) {
							mXmppConnectionService.databaseBackend.insertDiscoveryResult(result);
						}
						disco.put(jid, result);
						advancedStreamFeaturesLoaded = disco.containsKey(account.getServer())
								&& disco.containsKey(account.getJid().toBareJid());
					}
					if (advancedStreamFeaturesLoaded && (jid.equals(account.getServer()) || jid.equals(account.getJid().toBareJid()))) {
						enableAdvancedStreamFeatures();
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": could not query disco info for " + jid.toString());
				}
				if (packet.getType() != IqPacket.TYPE.TIMEOUT) {
					if (mPendingServiceDiscoveries.decrementAndGet() == 0
							&& mWaitForDisco.compareAndSet(true, false)) {
						finalizeBind();
					}
				}
			}
		});
	}

	private void finalizeBind() {
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": online with resource " + account.getResource());
		if (bindListener != null) {
			bindListener.onBind(account);
		}
		changeStatus(Account.State.ONLINE);
	}

	private void enableAdvancedStreamFeatures() {
		if (getFeatures().carbons() && !features.carbonsEnabled) {
			sendEnableCarbons();
		}
		if (getFeatures().blocking() && !features.blockListRequested) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": Requesting block list");
			this.sendIqPacket(getIqGenerator().generateGetBlockList(), mXmppConnectionService.getIqParser());
		}
		for (final OnAdvancedStreamFeaturesLoaded listener : advancedStreamFeaturesLoadedListeners) {
			listener.onAdvancedStreamFeaturesAvailable(account);
		}
	}

	private void sendServiceDiscoveryItems(final Jid server) {
		mPendingServiceDiscoveries.incrementAndGet();
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.setTo(server.toDomainJid());
		iq.query("http://jabber.org/protocol/disco#items");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					HashSet<Jid> items = new HashSet<Jid>();
					final List<Element> elements = packet.query().getChildren();
					for (final Element element : elements) {
						if (element.getName().equals("item")) {
							final Jid jid = element.getAttributeAsJid("jid");
							if (jid != null && !jid.equals(account.getServer())) {
								items.add(jid);
							}
						}
					}
					for(Jid jid : items) {
						sendServiceDiscoveryInfo(jid);
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": could not query disco items of " + server);
				}
				if (packet.getType() != IqPacket.TYPE.TIMEOUT) {
					if (mPendingServiceDiscoveries.decrementAndGet() == 0
							&& mWaitForDisco.compareAndSet(true, false)) {
						finalizeBind();
					}
				}
			}
		});
	}

	private void sendEnableCarbons() {
		final IqPacket iq = new IqPacket(IqPacket.TYPE.SET);
		iq.addChild("enable", "urn:xmpp:carbons:2");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (!packet.hasChild("error")) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": successfully enabled carbons");
					features.carbonsEnabled = true;
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": error enableing carbons " + packet.toString());
				}
			}
		});
	}

	private void processStreamError(final Tag currentTag)
		throws XmlPullParserException, IOException {
		final Element streamError = tagReader.readElement(currentTag);
		if (streamError == null) {
			return;
		}
		if (streamError.hasChild("conflict")) {
			final String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
					+ account.getResource() + ")");
			throw new IOException();
		} else if (streamError.hasChild("host-unknown")) {
			throw new StateChangingException(Account.State.HOST_UNKNOWN);
		} else if (streamError.hasChild("policy-violation")) {
			throw new StateChangingException(Account.State.POLICY_VIOLATION);
		} else {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": stream error "+streamError.toString());
			throw new StateChangingException(Account.State.STREAM_ERROR);
		}
	}

	private void sendStartStream() throws IOException {
		final Tag stream = Tag.start("stream:stream");
		stream.setAttribute("to", account.getServer().toString());
		stream.setAttribute("version", "1.0");
		stream.setAttribute("xml:lang", "en");
		stream.setAttribute("xmlns", "jabber:client");
		stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
		tagWriter.writeTag(stream);
	}

	private String nextRandomId() {
		return new BigInteger(50, mXmppConnectionService.getRNG()).toString(36);
	}

	public String sendIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
		packet.setFrom(account.getJid());
		return this.sendUnmodifiedIqPacket(packet, callback);
	}

	public synchronized String sendUnmodifiedIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
		if (packet.getId() == null) {
			packet.setAttribute("id", nextRandomId());
		}
		if (callback != null) {
			synchronized (this.packetCallbacks) {
				packetCallbacks.put(packet.getId(), new Pair<>(packet, callback));
			}
		}
		this.sendPacket(packet);
		return packet.getId();
	}

	public void sendMessagePacket(final MessagePacket packet) {
		this.sendPacket(packet);
	}

	public void sendPresencePacket(final PresencePacket packet) {
		this.sendPacket(packet);
	}

	private synchronized void sendPacket(final AbstractStanza packet) {
		if (stanzasSent == Integer.MAX_VALUE) {
			resetStreamId();
			disconnect(true);
			return;
		}
		synchronized (this.mStanzaQueue) {
			tagWriter.writeStanzaAsync(packet);
			if (packet instanceof AbstractAcknowledgeableStanza) {
				AbstractAcknowledgeableStanza stanza = (AbstractAcknowledgeableStanza) packet;
				++stanzasSent;
				this.mStanzaQueue.append(stanzasSent, stanza);
				if (stanza instanceof MessagePacket && stanza.getId() != null && getFeatures().sm()) {
					if (Config.EXTENDED_SM_LOGGING) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": requesting ack for message stanza #" + stanzasSent);
					}
					tagWriter.writeStanzaAsync(new RequestPacket(this.smVersion));
				}
			}
		}
	}

	public void sendPing() {
		if (!r()) {
			final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
			iq.setFrom(account.getJid());
			iq.addChild("ping", "urn:xmpp:ping");
			this.sendIqPacket(iq, null);
		}
		this.lastPingSent = SystemClock.elapsedRealtime();
	}

	public void setOnMessagePacketReceivedListener(
			final OnMessagePacketReceived listener) {
		this.messageListener = listener;
			}

	public void setOnUnregisteredIqPacketReceivedListener(
			final OnIqPacketReceived listener) {
		this.unregisteredIqListener = listener;
			}

	public void setOnPresencePacketReceivedListener(
			final OnPresencePacketReceived listener) {
		this.presenceListener = listener;
			}

	public void setOnJinglePacketReceivedListener(
			final OnJinglePacketReceived listener) {
		this.jingleListener = listener;
			}

	public void setOnStatusChangedListener(final OnStatusChanged listener) {
		this.statusListener = listener;
	}

	public void setOnBindListener(final OnBindListener listener) {
		this.bindListener = listener;
	}

	public void setOnMessageAcknowledgeListener(final OnMessageAcknowledged listener) {
		this.acknowledgedListener = listener;
	}

	public void addOnAdvancedStreamFeaturesAvailableListener(final OnAdvancedStreamFeaturesLoaded listener) {
		if (!this.advancedStreamFeaturesLoadedListeners.contains(listener)) {
			this.advancedStreamFeaturesLoadedListeners.add(listener);
		}
	}

	private void forceCloseSocket() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": io exception "+e.getMessage()+" during force close");
			}
		} else {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": socket was null during force close");
		}
	}

	public void interrupt() {
		Thread.currentThread().interrupt();
	}

	public void disconnect(final boolean force) {
		interrupt();
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting force="+Boolean.valueOf(force));
		if (force) {
			forceCloseSocket();
		} else {
			if (tagWriter.isActive()) {
				tagWriter.finish();
				try {
					int i = 0;
					boolean warned = false;
					while (!tagWriter.finished() && socket.isConnected() && i <= 10) {
						if (!warned) {
							Log.d(Config.LOGTAG, account.getJid().toBareJid()+": waiting for tag writer to finish");
							warned = true;
						}
						try {
							Thread.sleep(200);
						} catch(InterruptedException e) {
							Log.d(Config.LOGTAG,account.getJid().toBareJid()+": sleep interrupted");
						}
						i++;
					}
					if (warned) {
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": tag writer has finished");
					}
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": closing stream");
					tagWriter.writeTag(Tag.end("stream:stream"));
				} catch (final IOException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": io exception during disconnect ("+e.getMessage()+")");
				} finally {
					forceCloseSocket();
				}
			}
		}
	}

	public void resetStreamId() {
		this.streamId = null;
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
			this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
			return true;
		} else {
			return false;
		}
	}

	public String getMucServer() {
		synchronized (this.disco) {
			for (final Entry<Jid, ServiceDiscoveryResult> cursor : disco.entrySet()) {
				final ServiceDiscoveryResult value = cursor.getValue();
				if (value.getFeatures().contains("http://jabber.org/protocol/muc")
						&& !value.getFeatures().contains("jabber:iq:gateway")
						&& !value.hasIdentity("conference", "irc")) {
					return cursor.getKey().toString();
				}
			}
		}
		return null;
	}

	public int getTimeToNextAttempt() {
		final int interval = Math.min((int) (25 * Math.pow(1.3, attempt)), 300);
		final int secondsSinceLast = (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
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
		this.sendPacket(new ActivePacket());
	}

	public void sendInactive() {
		this.sendPacket(new InactivePacket());
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

	public Identity getServerIdentity() {
		synchronized (this.disco) {
			ServiceDiscoveryResult result = disco.get(account.getJid().toDomainJid());
			if (result == null) {
				return Identity.UNKNOWN;
			}
			for (final ServiceDiscoveryResult.Identity id : result.getIdentities()) {
				if (id.getType().equals("im") && id.getCategory().equals("server") && id.getName() != null) {
					switch (id.getName()) {
						case "Prosody":
							return Identity.PROSODY;
						case "ejabberd":
							return Identity.EJABBERD;
						case "Slack-XMPP":
							return Identity.SLACK;
					}
				}
			}
		}
		return Identity.UNKNOWN;
	}

	private class StateChangingError extends Error {
		private final Account.State state;

		public StateChangingError(Account.State state) {
			this.state = state;
		}
	}

	private class StateChangingException extends IOException {
		private final Account.State state;

		public StateChangingException(Account.State state) {
			this.state = state;
		}
	}

	public enum Identity {
		FACEBOOK,
		SLACK,
		EJABBERD,
		PROSODY,
		NIMBUZZ,
		UNKNOWN
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
				return connection.disco.containsKey(server) &&
						connection.disco.get(server).getFeatures().contains(feature);
			}
		}

		public boolean carbons() {
			return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
		}

		public boolean blocking() {
			return hasDiscoFeature(account.getServer(), Namespace.BLOCKING);
		}

		public boolean spamReporting() {
			return hasDiscoFeature(account.getServer(), "urn:xmpp:reporting:reason:spam:0");
		}

		public boolean register() {
			return hasDiscoFeature(account.getServer(), Namespace.REGISTER);
		}

		public boolean sm() {
			return streamId != null
					|| (connection.streamFeatures != null && connection.streamFeatures.hasChild("sm"));
		}

		public boolean csi() {
			return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "urn:xmpp:csi:0");
		}

		public boolean pep() {
			synchronized (XmppConnection.this.disco) {
				ServiceDiscoveryResult info = disco.get(account.getJid().toBareJid());
				return info != null && info.hasIdentity("pubsub", "pep");
			}
		}

		public boolean pepPersistent() {
			synchronized (XmppConnection.this.disco) {
				ServiceDiscoveryResult info = disco.get(account.getJid().toBareJid());
				return info != null && info.getFeatures().contains("http://jabber.org/protocol/pubsub#persistent-items");
			}
		}

		public boolean pepPublishOptions() {
			synchronized (XmppConnection.this.disco) {
				ServiceDiscoveryResult info = disco.get(account.getJid().toBareJid());
				return info != null && info.getFeatures().contains(Namespace.PUBSUB_PUBLISH_OPTIONS);
			}
		}

		public boolean mam() {
			return hasDiscoFeature(account.getJid().toBareJid(), Namespace.MAM)
					|| hasDiscoFeature(account.getJid().toBareJid(), Namespace.MAM_LEGACY);
		}

		public boolean mamLegacy() {
			return !hasDiscoFeature(account.getJid().toBareJid(), Namespace.MAM)
					&& hasDiscoFeature(account.getJid().toBareJid(), Namespace.MAM_LEGACY);
		}

		public boolean push() {
			return hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:push:0")
					|| hasDiscoFeature(account.getServer(), "urn:xmpp:push:0");
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
				List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Namespace.HTTP_UPLOAD);
				if (items.size() > 0) {
					try {
						long maxsize = Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Namespace.HTTP_UPLOAD, "max-file-size"));
						if(filesize <= maxsize) {
							return true;
						} else {
							Log.d(Config.LOGTAG,account.getJid().toBareJid()+": http upload is not available for files with size "+filesize+" (max is "+maxsize+")");
							return false;
						}
					} catch (Exception e) {
						return true;
					}
				} else {
					return false;
				}
			}
		}

		public long getMaxHttpUploadSize() {
			List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Namespace.HTTP_UPLOAD);
				if (items.size() > 0) {
					try {
						return Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Namespace.HTTP_UPLOAD, "max-file-size"));
					} catch (Exception e) {
						return -1;
					}
				} else {
					return -1;
				}
		}

		public boolean stanzaIds() {
			return hasDiscoFeature(account.getJid().toBareJid(), Namespace.STANZA_IDS);
		}
	}

	private IqGenerator getIqGenerator() {
		return mXmppConnectionService.getIqGenerator();
	}
}
