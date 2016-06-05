package eu.siacs.conversations.xmpp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.security.KeyChain;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import org.json.JSONException;
import org.json.JSONObject;
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
import java.net.UnknownHostException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.XmppDomainVerifier;
import eu.siacs.conversations.crypto.sasl.DigestMd5;
import eu.siacs.conversations.crypto.sasl.External;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import eu.siacs.conversations.crypto.sasl.ScramSha1;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.DNSHelper;
import eu.siacs.conversations.utils.SSLSocketHelper;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;
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
	protected Account account;
	private final WakeLock wakeLock;
	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter;
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
	private AtomicBoolean mIsServiceItemsDiscoveryPending = new AtomicBoolean(true);
	private boolean mWaitForDisco = true;
	private final ArrayList<String> mPendingServiceDiscoveriesIds = new ArrayList<>();
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
	private XmppConnectionService mXmppConnectionService = null;

	private SaslMechanism saslMechanism;

	private X509KeyManager mKeyManager = new X509KeyManager() {
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
			try {
				return KeyChain.getCertificateChain(mXmppConnectionService, alias);
			} catch (Exception e) {
				return new X509Certificate[0];
			}
		}

		@Override
		public String[] getClientAliases(String s, Principal[] principals) {
			return new String[0];
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
	};
	private Identity mServerIdentity = Identity.UNKNOWN;

	public final OnIqPacketReceived registrationResponseListener =  new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE.RESULT) {
				account.setOption(Account.OPTION_REGISTER, false);
				forceCloseSocket();
				changeStatus(Account.State.REGISTRATION_SUCCESSFUL);
			} else {
				Element error = packet.findChild("error");
				if (error != null && error.hasChild("conflict")) {
					forceCloseSocket();
					changeStatus(Account.State.REGISTRATION_CONFLICT);
				} else if (error != null
						&& "wait".equals(error.getAttribute("type"))
						&& error.hasChild("resource-constraint")) {
					forceCloseSocket();
					changeStatus(Account.State.REGISTRATION_PLEASE_WAIT);
				} else {
					forceCloseSocket();
					changeStatus(Account.State.REGISTRATION_FAILED);
					Log.d(Config.LOGTAG, packet.toString());
				}
			}
		}
	};

	public XmppConnection(final Account account, final XmppConnectionService service) {
		this.account = account;
		this.wakeLock = service.getPowerManager().newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, account.getJid().toBareJid().toString());
		tagWriter = new TagWriter();
		mXmppConnectionService = service;
	}

	protected void changeStatus(final Account.State nextStatus) {
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
			if (statusListener != null) {
				statusListener.onStatusChanged(account);
			}
		}
	}

	public void prepareNewConnection() {
		this.lastConnect = SystemClock.elapsedRealtime();
		this.lastPingSent = SystemClock.elapsedRealtime();
		this.lastDiscoStarted = Long.MAX_VALUE;
		this.changeStatus(Account.State.CONNECTING);
	}

	protected void connect() {
		Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": connecting");
		features.encryptionEnabled = false;
		this.attempt++;
		switch (account.getJid().getDomainpart()) {
			case "chat.facebook.com":
				mServerIdentity = Identity.FACEBOOK;
				break;
			case "nimbuzz.com":
				mServerIdentity = Identity.NIMBUZZ;
				break;
			default:
				mServerIdentity = Identity.UNKNOWN;
				break;
		}
		try {
			shouldAuthenticate = needsBinding = !account.isOptionSet(Account.OPTION_REGISTER);
			tagReader = new XmlReader(wakeLock);
			tagWriter = new TagWriter();
			this.changeStatus(Account.State.CONNECTING);
			final boolean useTor = mXmppConnectionService.useTorToConnect() || account.isOnion();
			final boolean extended = mXmppConnectionService.showExtendedConnectionOptions();
			if (useTor) {
				String destination;
				if (account.getHostname() == null || account.getHostname().isEmpty()) {
					destination = account.getServer().toString();
				} else {
					destination = account.getHostname();
				}
				Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": connect to " + destination + " via TOR");
				socket = SocksSocketFactory.createSocketOverTor(destination, account.getPort());
				startXmpp();
			} else if (extended && account.getHostname() != null && !account.getHostname().isEmpty()) {
				socket = new Socket();
				try {
					socket.connect(new InetSocketAddress(account.getHostname(), account.getPort()), Config.SOCKET_TIMEOUT * 1000);
				} catch (IOException e) {
					throw new UnknownHostException();
				}
				startXmpp();
			} else if (DNSHelper.isIp(account.getServer().toString())) {
				socket = new Socket();
				try {
					socket.connect(new InetSocketAddress(account.getServer().toString(), 5222), Config.SOCKET_TIMEOUT * 1000);
				} catch (IOException e) {
					throw new UnknownHostException();
				}
				startXmpp();
			} else {
				final Bundle result = DNSHelper.getSRVRecord(account.getServer(), mXmppConnectionService);
				final ArrayList<Parcelable>values = result.getParcelableArrayList("values");
				for(Iterator<Parcelable> iterator = values.iterator(); iterator.hasNext();) {
					if (Thread.currentThread().isInterrupted()) {
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": Thread was interrupted");
						return;
					}
					final Bundle namePort = (Bundle) iterator.next();
					try {
						String srvRecordServer;
						try {
							srvRecordServer = IDN.toASCII(namePort.getString("name"));
						} catch (final IllegalArgumentException e) {
							// TODO: Handle me?`
							srvRecordServer = "";
						}
						final int srvRecordPort = namePort.getInt("port");
						final String srvIpServer = namePort.getString("ip");
						// if tls is true, encryption is implied and must not be started
						features.encryptionEnabled = namePort.getBoolean("tls");
						final InetSocketAddress addr;
						if (srvIpServer != null) {
							addr = new InetSocketAddress(srvIpServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns " + srvRecordServer
									+ "[" + srvIpServer + "]:" + srvRecordPort + " tls: " + features.encryptionEnabled);
						} else {
							addr = new InetSocketAddress(srvRecordServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns "
									+ srvRecordServer + ":" + srvRecordPort + " tls: " + features.encryptionEnabled);
						}

						if (!features.encryptionEnabled) {
							socket = new Socket();
							socket.connect(addr, Config.SOCKET_TIMEOUT * 1000);
						} else {
							final TlsFactoryVerifier tlsFactoryVerifier = getTlsFactoryVerifier();
							socket = tlsFactoryVerifier.factory.createSocket();

							if (socket == null) {
								throw new IOException("could not initialize ssl socket");
							}

							SSLSocketHelper.setSecurity((SSLSocket) socket);
							SSLSocketHelper.setSNIHost(tlsFactoryVerifier.factory, (SSLSocket) socket, account.getServer().getDomainpart());
							SSLSocketHelper.setAlpnProtocol(tlsFactoryVerifier.factory, (SSLSocket) socket, "xmpp-client");

							socket.connect(addr, Config.SOCKET_TIMEOUT * 1000);

							if (!tlsFactoryVerifier.verifier.verify(account.getServer().getDomainpart(), ((SSLSocket) socket).getSession())) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": TLS certificate verification failed");
								throw new SecurityException();
							}
						}

						if (startXmpp())
							break; // successfully connected to server that speaks xmpp
					} catch(final SecurityException e) {
						throw e;
					} catch (final Throwable e) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage() +"("+e.getClass().getName()+")");
						if (!iterator.hasNext()) {
							throw new UnknownHostException();
						}
					}
				}
			}
			processStream();
		} catch (final IncompatibleServerException e) {
			this.changeStatus(Account.State.INCOMPATIBLE_SERVER);
		} catch (final SecurityException e) {
			this.changeStatus(Account.State.SECURITY_ERROR);
		} catch (final UnauthorizedException e) {
			this.changeStatus(Account.State.UNAUTHORIZED);
		} catch (final UnknownHostException | ConnectException e) {
			this.changeStatus(Account.State.SERVER_NOT_FOUND);
		} catch (final SocksSocketFactory.SocksProxyNotFoundException e) {
			this.changeStatus(Account.State.TOR_NOT_AVAILABLE);
		} catch (final IOException | XmlPullParserException | NoSuchAlgorithmException e) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage());
			this.changeStatus(Account.State.OFFLINE);
			this.attempt = Math.max(0, this.attempt - 1);
		} finally {
			forceCloseSocket();
			if (wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (final RuntimeException ignored) {
				}
			}
		}
	}

	/**
	 * Starts xmpp protocol, call after connecting to socket
	 * @return true if server returns with valid xmpp, false otherwise
	 * @throws IOException Unknown tag on connect
	 * @throws XmlPullParserException Bad Xml
	 * @throws NoSuchAlgorithmException Other error
     */
	private boolean startXmpp() throws IOException, XmlPullParserException, NoSuchAlgorithmException {
		tagWriter.setOutputStream(socket.getOutputStream());
		tagReader.setInputStream(socket.getInputStream());
		tagWriter.beginDocument();
		sendStartStream();
		Tag nextTag;
		while ((nextTag = tagReader.readTag()) != null) {
			if (nextTag.isStart("stream")) {
				return true;
			} else {
				throw new IOException("unknown tag on connect");
			}
		}
		if (socket.isConnected()) {
			socket.close();
		}
		return false;
	}

	private static class TlsFactoryVerifier {
		private final SSLSocketFactory factory;
		private final HostnameVerifier verifier;

		public TlsFactoryVerifier(final SSLSocketFactory factory, final HostnameVerifier verifier) throws IOException {
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
			keyManager = new KeyManager[]{mKeyManager};
		} else {
			keyManager = null;
		}
		sc.init(keyManager, new X509TrustManager[]{mInteractive ? trustManager : trustManager.getNonInteractive()}, mXmppConnectionService.getRNG());
		final SSLSocketFactory factory = sc.getSocketFactory();
		final HostnameVerifier verifier;
		if (mInteractive) {
			verifier = trustManager.wrapHostnameVerifier(new XmppDomainVerifier());
		} else {
			verifier = trustManager.wrapHostnameVerifierNonInteractive(new XmppDomainVerifier());
		}

		return new TlsFactoryVerifier(factory, verifier);
	}

	@Override
	public void run() {
		forceCloseSocket();
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
					disconnect(true);
					Log.e(Config.LOGTAG, String.valueOf(e));
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
				throw new UnauthorizedException();
			} else if (nextTag.isStart("challenge")) {
				final String challenge = tagReader.readElement(nextTag).getContent();
				final Element response = new Element("response");
				response.setAttribute("xmlns",
						"urn:ietf:params:xml:ns:xmpp-sasl");
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
				lastPacketReceived = SystemClock.elapsedRealtime();
				final Element resumed = tagReader.readElement(nextTag);
				final String h = resumed.getAttribute("h");
				try {
					final int serverCount = Integer.parseInt(h);
					if (serverCount != stanzasSent) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
								+ ": session resumed with lost packages");
						stanzasSent = serverCount;
					} else {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": session resumed");
					}
					acknowledgeStanzaUpTo(serverCount);
					ArrayList<AbstractAcknowledgeableStanza> failedStanzas = new ArrayList<>();
					for(int i = 0; i < this.mStanzaQueue.size(); ++i) {
						failedStanzas.add(mStanzaQueue.valueAt(i));
					}
					mStanzaQueue.clear();
					Log.d(Config.LOGTAG,"resending "+failedStanzas.size()+" stanzas");
					for(AbstractAcknowledgeableStanza packet : failedStanzas) {
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
				final Element ack = tagReader.readElement(nextTag);
				lastPacketReceived = SystemClock.elapsedRealtime();
				try {
					final int serverSequence = Integer.parseInt(ack.getAttribute("h"));
					acknowledgeStanzaUpTo(serverSequence);
				} catch (NumberFormatException | NullPointerException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": server send ack without sequence number");
				}
			} else if (nextTag.isStart("failed")) {
				Element failed = tagReader.readElement(nextTag);
				try {
					final int serverCount = Integer.parseInt(failed.getAttribute("h"));
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": resumption failed but server acknowledged stanza #"+serverCount);
					acknowledgeStanzaUpTo(serverCount);
				} catch (NumberFormatException | NullPointerException e) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": resumption failed");
				}
				resetStreamId();
				if (account.getStatus() != Account.State.ONLINE) {
					sendBindRequest();
				}
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
						if (packet.fromServer(account) || mServerIdentity == Identity.FACEBOOK) {
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
				callback.onIqPacketReceived(account,packet);
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
		startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
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

			if (!tlsFactoryVerifier.verifier.verify(account.getServer().getDomainpart(), sslSocket.getSession())) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": TLS certificate verification failed");
				throw new SecurityException();
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
			throw new SecurityException();
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
				throw new IncompatibleServerException();
			}
		} else if (!this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)) {
			forceCloseSocket();
			changeStatus(Account.State.REGISTRATION_NOT_SUPPORTED);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate
				&& (features.encryptionEnabled || Config.ALLOW_NON_TLS_CONNECTIONS)) {
			authenticate();
		} else if (this.streamFeatures.hasChild("sm", "urn:xmpp:sm:" + smVersion) && streamId != null) {
			if (Config.EXTENDED_SM_LOGGING) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": resuming after stanza #"+stanzasReceived);
			}
			final ResumePacket resume = new ResumePacket(this.streamId, stanzasReceived, smVersion);
			this.tagWriter.writeStanzaAsync(resume);
		} else if (needsBinding) {
			if (this.streamFeatures.hasChild("bind")) {
				sendBindRequest();
			} else {
				throw new IncompatibleServerException();
			}
		}
	}

	private void authenticate() throws IOException {
		final List<String> mechanisms = extractMechanisms(streamFeatures
				.findChild("mechanisms"));
		final Element auth = new Element("auth");
		auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		if (mechanisms.contains("EXTERNAL") && account.getPrivateKeyAlias() != null) {
			saslMechanism = new External(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("SCRAM-SHA-1")) {
			saslMechanism = new ScramSha1(tagWriter, account, mXmppConnectionService.getRNG());
		} else if (mechanisms.contains("PLAIN")) {
			saslMechanism = new Plain(tagWriter, account);
		} else if (mechanisms.contains("DIGEST-MD5")) {
			saslMechanism = new DigestMd5(tagWriter, account, mXmppConnectionService.getRNG());
		}
		if (saslMechanism != null) {
			final JSONObject keys = account.getKeys();
			try {
				if (keys.has(Account.PINNED_MECHANISM_KEY) &&
						keys.getInt(Account.PINNED_MECHANISM_KEY) > saslMechanism.getPriority()) {
					Log.e(Config.LOGTAG, "Auth failed. Authentication mechanism " + saslMechanism.getMechanism() +
							" has lower priority (" + String.valueOf(saslMechanism.getPriority()) +
							") than pinned priority (" + keys.getInt(Account.PINNED_MECHANISM_KEY) +
							"). Possible downgrade attack?");
					throw new SecurityException();
				}
			} catch (final JSONException e) {
				Log.d(Config.LOGTAG, "Parse error while checking pinned auth mechanism");
			}
			Log.d(Config.LOGTAG, account.getJid().toString() + ": Authenticating with " + saslMechanism.getMechanism());
			auth.setAttribute("mechanism", saslMechanism.getMechanism());
			if (!saslMechanism.getClientFirstMessage().isEmpty()) {
				auth.setContent(saslMechanism.getClientFirstMessage());
			}
			tagWriter.writeElement(auth);
		} else {
			throw new IncompatibleServerException();
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
		sendIqPacket(register, new OnIqPacketReceived() {

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
					sendIqPacket(register, registrationResponseListener);
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
					final Element instructions = packet.query().findChild("instructions");
					setAccountCreationFailed((instructions != null) ? instructions.getContent() : "");
				}
			}
		});
	}

	private void setAccountCreationFailed(String instructions) {
		changeStatus(Account.State.REGISTRATION_FAILED);
		disconnect(true);
		Log.d(Config.LOGTAG, account.getJid().toBareJid()
				+ ": could not register. instructions are"
				+ instructions);
	}

	public void resetEverything() {
		resetAttemptCount();
		resetStreamId();
		clearIqCallbacks();
		mStanzaQueue.clear();
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
							account.setResource(Jid.fromString(jid.getContent()).getResourcepart());
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
				forceCloseSocket();
				changeStatus(Account.State.BIND_FAILURE);
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
			callback.onIqPacketReceived(account,failurePacket);
		}
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": done clearing iq callbacks. " + this.packetCallbacks.size() + " left");
	}

	public void sendDiscoTimeout() {
		final IqPacket failurePacket = new IqPacket(IqPacket.TYPE.ERROR); //don't use timeout
		final ArrayList<OnIqPacketReceived> callbacks = new ArrayList<>();
		synchronized (this.mPendingServiceDiscoveriesIds) {
			for(String id : mPendingServiceDiscoveriesIds) {
				synchronized (this.packetCallbacks) {
					Pair<IqPacket, OnIqPacketReceived> pair = this.packetCallbacks.remove(id);
					if (pair != null) {
						callbacks.add(pair.second);
					}
				}
			}
			this.mPendingServiceDiscoveriesIds.clear();
		}
		if (callbacks.size() > 0) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": sending disco timeout");
			resetStreamId(); //we don't want to live with this for ever
		}
		for(OnIqPacketReceived callback : callbacks) {
			callback.onIqPacketReceived(account,failurePacket);
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
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": could not init sessions");
					disconnect(true);
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
			final EnablePacket enable = new EnablePacket(smVersion);
			tagWriter.writeStanzaAsync(enable);
			stanzasSent = 0;
			mStanzaQueue.clear();
		}
		features.carbonsEnabled = false;
		features.blockListRequested = false;
		synchronized (this.disco) {
			this.disco.clear();
		}
		mPendingServiceDiscoveries.set(0);
		mIsServiceItemsDiscoveryPending.set(true);
		mWaitForDisco = mServerIdentity != Identity.NIMBUZZ;
		lastDiscoStarted = SystemClock.elapsedRealtime();
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": starting service discovery");
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
		if (!mWaitForDisco) {
			finalizeBind();
		}
		this.lastSessionStarted = SystemClock.elapsedRealtime();
	}

	private void sendServiceDiscoveryInfo(final Jid jid) {
		mPendingServiceDiscoveries.incrementAndGet();
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.setTo(jid);
		iq.query("http://jabber.org/protocol/disco#info");
		String id = this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					boolean advancedStreamFeaturesLoaded;
					synchronized (XmppConnection.this.disco) {
						ServiceDiscoveryResult result = new ServiceDiscoveryResult(packet);
						for (final ServiceDiscoveryResult.Identity id : result.getIdentities()) {
							if (mServerIdentity == Identity.UNKNOWN && id.getType().equals("im") &&
							    id.getCategory().equals("server") && id.getName() != null &&
							    jid.equals(account.getServer())) {
									switch (id.getName()) {
										case "Prosody":
											mServerIdentity = Identity.PROSODY;
											break;
										case "ejabberd":
											mServerIdentity = Identity.EJABBERD;
											break;
										case "Slack-XMPP":
											mServerIdentity = Identity.SLACK;
											break;
									}
									Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": server name: " + id.getName());
								}
						}
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
							&& !mIsServiceItemsDiscoveryPending.get()
							&& mWaitForDisco) {
						finalizeBind();
					}
				}
			}
		});
		synchronized (this.mPendingServiceDiscoveriesIds) {
			this.mPendingServiceDiscoveriesIds.add(id);
		}
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
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.setTo(server.toDomainJid());
		iq.query("http://jabber.org/protocol/disco#items");
		String id = this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					final List<Element> elements = packet.query().getChildren();
					for (final Element element : elements) {
						if (element.getName().equals("item")) {
							final Jid jid = element.getAttributeAsJid("jid");
							if (jid != null && !jid.equals(account.getServer())) {
								sendServiceDiscoveryInfo(jid);
							}
						}
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": could not query disco items of " + server);
				}
				if (packet.getType() != IqPacket.TYPE.TIMEOUT) {
					mIsServiceItemsDiscoveryPending.set(false);
					if (mPendingServiceDiscoveries.get() == 0 && mWaitForDisco) {
						finalizeBind();
					}
				}
			}
		});
		synchronized (this.mPendingServiceDiscoveriesIds) {
			this.mPendingServiceDiscoveriesIds.add(id);
		}
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
		Log.d(Config.LOGTAG,account.getJid().toBareJid()+": stream error "+streamError.toString());
		if (streamError.hasChild("conflict")) {
			final String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
					+ account.getResource() + ")");
		} else if (streamError.hasChild("host-unknown")) {
			changeStatus(Account.State.HOST_UNKNOWN);
		}
		forceCloseSocket();
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
		return new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
	}

	public String sendIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
		packet.setFrom(account.getJid());
		return this.sendUnmodifiedIqPacket(packet, callback);
	}

	private synchronized String sendUnmodifiedIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
		if (packet.getId() == null) {
			final String id = nextRandomId();
			packet.setAttribute("id", id);
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
		tagWriter.writeStanzaAsync(packet);
		if (packet instanceof AbstractAcknowledgeableStanza) {
			AbstractAcknowledgeableStanza stanza = (AbstractAcknowledgeableStanza) packet;
			++stanzasSent;
			this.mStanzaQueue.put(stanzasSent, stanza);
			if (stanza instanceof MessagePacket && stanza.getId() != null && getFeatures().sm()) {
				if (Config.EXTENDED_SM_LOGGING) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": requesting ack for message stanza #" + stanzasSent);
				}
				tagWriter.writeStanzaAsync(new RequestPacket(this.smVersion));
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

	public void waitForPush() {
		if (tagWriter.isActive()) {
			tagWriter.finish();
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						while(!tagWriter.finished()) {
							Thread.sleep(10);
						}
						socket.close();
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": closed tcp without closing stream");
					} catch (IOException | InterruptedException e) {
						return;
					}
				}
			}).start();
		} else {
			forceCloseSocket();
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": closed tcp without closing stream (no waiting)");
		}
	}

	private void forceCloseSocket() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
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
			return;
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
						Thread.sleep(200);
						i++;
					}
					if (warned) {
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": tag writer has finished");
					}
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": closing stream");
					tagWriter.writeTag(Tag.end("stream:stream"));
				} catch (final IOException e) {
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": io exception during disconnect ("+e.getMessage()+")");
				} catch (final InterruptedException e) {
					Log.d(Config.LOGTAG, "interrupted");
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

	public void resetAttemptCount() {
		this.attempt = 0;
		this.lastConnect = 0;
	}

	public void setInteractive(boolean interactive) {
		this.mInteractive = interactive;
	}

	public Identity getServerIdentity() {
		return mServerIdentity;
	}

	private class UnauthorizedException extends IOException {

	}

	private class SecurityException extends IOException {

	}

	private class IncompatibleServerException extends IOException {

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
			return hasDiscoFeature(account.getServer(), Xmlns.BLOCKING);
		}

		public boolean register() {
			return hasDiscoFeature(account.getServer(), Xmlns.REGISTER);
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

		public boolean mam() {
			return hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:mam:0")
				|| hasDiscoFeature(account.getServer(), "urn:xmpp:mam:0");
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
				List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD);
				if (items.size() > 0) {
					try {
						long maxsize = Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Xmlns.HTTP_UPLOAD, "max-file-size"));
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
			List<Entry<Jid, ServiceDiscoveryResult>> items = findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD);
				if (items.size() > 0) {
					try {
						return Long.parseLong(items.get(0).getValue().getExtendedDiscoInformation(Xmlns.HTTP_UPLOAD, "max-file-size"));
					} catch (Exception e) {
						return -1;
					}
				} else {
					return -1;
				}
		}
	}

	private IqGenerator getIqGenerator() {
		return mXmppConnectionService.getIqGenerator();
	}
}
