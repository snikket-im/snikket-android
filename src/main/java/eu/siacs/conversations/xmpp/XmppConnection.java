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
import java.io.OutputStream;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

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
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.DNSHelper;
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
	private final HashMap<Jid, Info> disco = new HashMap<>();

	private String streamId = null;
	private int smVersion = 3;
	private final SparseArray<AbstractAcknowledgeableStanza> mStanzaQueue = new SparseArray<>();

	private int stanzasReceived = 0;
	private int stanzasSent = 0;
	private long lastPacketReceived = 0;
	private long lastPingSent = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private int mPendingServiceDiscoveries = 0;
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

	private OnIqPacketReceived createPacketReceiveHandler() {
		return new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					account.setOption(Account.OPTION_REGISTER,
							false);
					changeStatus(Account.State.REGISTRATION_SUCCESSFUL);
				} else if (packet.hasChild("error")
						&& (packet.findChild("error")
						.hasChild("conflict"))) {
					changeStatus(Account.State.REGISTRATION_CONFLICT);
				} else {
					changeStatus(Account.State.REGISTRATION_FAILED);
					Log.d(Config.LOGTAG, packet.toString());
				}
				disconnect(true);
			}
		};
	}

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

	protected void connect() {
		Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": connecting");
		features.encryptionEnabled = false;
		lastConnect = SystemClock.elapsedRealtime();
		lastPingSent = SystemClock.elapsedRealtime();
		this.attempt++;
		if (account.getJid().getDomainpart().equals("chat.facebook.com")) {
			mServerIdentity = Identity.FACEBOOK;
		}
		try {
			shouldAuthenticate = needsBinding = !account.isOptionSet(Account.OPTION_REGISTER);
			tagReader = new XmlReader(wakeLock);
			tagWriter = new TagWriter();
			this.changeStatus(Account.State.CONNECTING);
			final boolean useTor = mXmppConnectionService.useTorToConnect() || account.isOnion();
			if (useTor) {
				String destination;
				if (account.getHostname() == null || account.getHostname().isEmpty()) {
					destination = account.getServer().toString();
				} else {
					destination = account.getHostname();
				}
				Log.d(Config.LOGTAG,account.getJid().toBareJid()+": connect to "+destination+" via TOR");
				socket = SocksSocketFactory.createSocketOverTor(destination,account.getPort());
			} else if (DNSHelper.isIp(account.getServer().toString())) {
				socket = new Socket();
				try {
					socket.connect(new InetSocketAddress(account.getServer().toString(), 5222), Config.SOCKET_TIMEOUT * 1000);
				} catch (IOException e) {
					throw new UnknownHostException();
				}
			} else {
				final Bundle result = DNSHelper.getSRVRecord(account.getServer(),mXmppConnectionService);
				final ArrayList<Parcelable>values = result.getParcelableArrayList("values");
				int i = 0;
				boolean socketError = true;
				while (socketError && values.size() > i) {
					final Bundle namePort = (Bundle) values.get(i);
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
						final InetSocketAddress addr;
						if (srvIpServer != null) {
							addr = new InetSocketAddress(srvIpServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns " + srvRecordServer
									+ "[" + srvIpServer + "]:" + srvRecordPort);
						} else {
							addr = new InetSocketAddress(srvRecordServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
									+ ": using values from dns "
									+ srvRecordServer + ":" + srvRecordPort);
						}
						socket = new Socket();
						socket.connect(addr, Config.SOCKET_TIMEOUT * 1000);
						socketError = false;
					} catch (final Throwable e) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage() +"("+e.getClass().getName()+")");
						i++;
					}
				}
				if (socketError) {
					throw new UnknownHostException();
				}
			}
			final OutputStream out = socket.getOutputStream();
			tagWriter.setOutputStream(out);
			final InputStream in = socket.getInputStream();
			tagReader.setInputStream(in);
			tagWriter.beginDocument();
			sendStartStream();
			Tag nextTag;
			while ((nextTag = tagReader.readTag()) != null) {
				if (nextTag.isStart("stream")) {
					processStream();
					break;
				} else {
					throw new IOException("unknown tag on connect");
				}
			}
			if (socket.isConnected()) {
				socket.close();
			}
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
			this.attempt--; //don't count attempt when reconnecting instantly anyway
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {

				}
			}
			if (wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (final RuntimeException ignored) {
				}
			}
		}
	}

	@Override
	public void run() {
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (final IOException ignored) {

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
											+ ": stream managment(" + smVersion
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
								} catch (NumberFormatException e) {
									Log.d(Config.LOGTAG,account.getJid().toBareJid()+": server send ack without sequence number");
								}
							} else if (nextTag.isStart("failed")) {
								tagReader.readElement(nextTag);
								Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": resumption failed");
								streamId = null;
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
						Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": last tag was " + nextTag);
						if (account.getStatus() == Account.State.ONLINE) {
							account. setStatus(Account.State.OFFLINE);
							if (statusListener != null) {
								statusListener.onStatusChanged(account);
							}
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
			final SSLContext sc = SSLContext.getInstance("TLS");
			MemorizingTrustManager trustManager = this.mXmppConnectionService.getMemorizingTrustManager();
			KeyManager[] keyManager;
			if (account.getPrivateKeyAlias() != null && account.getPassword().isEmpty()) {
				keyManager = new KeyManager[]{ mKeyManager };
			} else {
				keyManager = null;
			}
			sc.init(keyManager,new X509TrustManager[]{mInteractive ? trustManager : trustManager.getNonInteractive()},mXmppConnectionService.getRNG());
			final SSLSocketFactory factory = sc.getSocketFactory();
			final HostnameVerifier verifier;
			if (mInteractive) {
				verifier = trustManager.wrapHostnameVerifier(new XmppDomainVerifier());
			} else {
				verifier = trustManager.wrapHostnameVerifierNonInteractive(new XmppDomainVerifier());
			}
			final InetAddress address = socket == null ? null : socket.getInetAddress();

			if (factory == null || address == null || verifier == null) {
				throw new IOException("could not setup ssl");
			}

			final SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket,address.getHostAddress(), socket.getPort(),true);

			if (sslSocket == null) {
				throw new IOException("could not initialize ssl socket");
			}

			final String[] supportProtocols;
			final Collection<String> supportedProtocols = new LinkedList<>(
					Arrays.asList(sslSocket.getSupportedProtocols()));
			supportedProtocols.remove("SSLv3");
			supportProtocols = supportedProtocols.toArray(new String[supportedProtocols.size()]);

			sslSocket.setEnabledProtocols(supportProtocols);

			final String[] cipherSuites = CryptoHelper.getOrderedCipherSuites(
					sslSocket.getSupportedCipherSuites());
			//Log.d(Config.LOGTAG, "Using ciphers: " + Arrays.toString(cipherSuites));
			if (cipherSuites.length > 0) {
				sslSocket.setEnabledCipherSuites(cipherSuites);
			}

			if (!verifier.verify(account.getServer().getDomainpart(),sslSocket.getSession())) {
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
		} else if (this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)
				&& features.encryptionEnabled) {
			sendRegistryRequest();
		} else if (!this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)) {
			changeStatus(Account.State.REGISTRATION_NOT_SUPPORTED);
			disconnect(true);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate && features.encryptionEnabled) {
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

	private List<String> extractMechanisms(final Element stream) {
		final ArrayList<String> mechanisms = new ArrayList<>(stream
				.getChildren().size());
		for (final Element child : stream.getChildren()) {
			mechanisms.add(child.getContent());
		}
		return mechanisms;
	}

	public void sendCaptchaRegistryRequest(String id, Data data) {
		if (data == null) {
			setAccountCreationFailed("");
		} else {
			IqPacket request = getIqGenerator().generateCreateAccountWithCaptcha(account, id, data);
			sendIqPacket(request, createPacketReceiveHandler());
		}
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
					sendIqPacket(register, createPacketReceiveHandler());
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
						} catch (final InvalidJidException e) {
							// TODO: Handle the case where an external JID is technically invalid?
						}
						if (streamFeatures.hasChild("session")) {
							sendStartSession();
						} else {
							sendPostBindInitialization();
						}
					} else {
						Log.d(Config.LOGTAG, account.getJid() + ": disconnecting because of bind failure");
						disconnect(true);
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid() + ": disconnecting because of bind failure");
					disconnect(true);
				}
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

	private void sendStartSession() {
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
		mPendingServiceDiscoveries = 0;
		sendServiceDiscoveryItems(account.getServer());
		sendServiceDiscoveryInfo(account.getServer());
		sendServiceDiscoveryInfo(account.getJid().toBareJid());
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": online with resource " + account.getResource());
		this.lastSessionStarted = SystemClock.elapsedRealtime();
	}

	private void sendServiceDiscoveryInfo(final Jid jid) {
		mPendingServiceDiscoveries++;
		final IqPacket iq = new IqPacket(IqPacket.TYPE.GET);
		iq.setTo(jid);
		iq.query("http://jabber.org/protocol/disco#info");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					boolean advancedStreamFeaturesLoaded = false;
					synchronized (XmppConnection.this.disco) {
						final List<Element> elements = packet.query().getChildren();
						final Info info = new Info();
						for (final Element element : elements) {
							if (element.getName().equals("identity")) {
								String type = element.getAttribute("type");
								String category = element.getAttribute("category");
								String name = element.getAttribute("name");
								if (type != null && category != null) {
									info.identities.add(new Pair<>(category, type));
									if (type.equals("im") && category.equals("server")) {
										if (name != null && jid.equals(account.getServer())) {
											switch (name) {
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
											Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": server name: " + name);
										}
									}
								}
							} else if (element.getName().equals("feature")) {
								info.features.add(element.getAttribute("var"));
							}
						}
						disco.put(jid, info);
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
					mPendingServiceDiscoveries--;
					if (mPendingServiceDiscoveries <= 0) {
						Log.d(Config.LOGTAG,account.getJid().toBareJid()+": done with service discovery");
						changeStatus(Account.State.ONLINE);
						if (bindListener != null) {
							bindListener.onBind(account);
						}
					}
				}
			}
		});
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
		this.sendIqPacket(iq, new OnIqPacketReceived() {

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
					Log.d(Config.LOGTAG,account.getJid().toBareJid()+": could not query disco items of "+server);
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
		if (streamError != null && streamError.hasChild("conflict")) {
			final String resource = account.getResource().split("\\.")[0];
			account.setResource(resource + "." + nextRandomId());
			Log.d(Config.LOGTAG,
					account.getJid().toBareJid() + ": switching resource due to conflict ("
					+ account.getResource() + ")");
		} else if (streamError != null) {
			Log.d(Config.LOGTAG,account.getJid().toBareJid()+": stream error "+streamError.toString());
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
		return new BigInteger(50, mXmppConnectionService.getRNG()).toString(32);
	}

	public void sendIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
		packet.setFrom(account.getJid());
		this.sendUnmodifiedIqPacket(packet, callback);

	}

	private synchronized void sendUnmodifiedIqPacket(final IqPacket packet, final OnIqPacketReceived callback) {
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

	public void disconnect(final boolean force) {
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting force="+Boolean.valueOf(force));
		if (force) {
			try {
				socket.close();
			} catch(Exception e) {
				Log.d(Config.LOGTAG,account.getJid().toBareJid().toString()+": exception during force close ("+e.getMessage()+")");
			}
			return;
		} else {
			resetStreamId();
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

	public List<Jid> findDiscoItemsByFeature(final String feature) {
		synchronized (this.disco) {
			final List<Jid> items = new ArrayList<>();
			for (final Entry<Jid, Info> cursor : this.disco.entrySet()) {
				if (cursor.getValue().features.contains(feature)) {
					items.add(cursor.getKey());
				}
			}
			return items;
		}
	}

	public Jid findDiscoItemByFeature(final String feature) {
		final List<Jid> items = findDiscoItemsByFeature(feature);
		if (items.size() >= 1) {
			return items.get(0);
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
			for (final Entry<Jid, Info> cursor : disco.entrySet()) {
				final Info value = cursor.getValue();
				if (value.features.contains("http://jabber.org/protocol/muc")
						&& !value.features.contains("jabber:iq:gateway")
						&& !value.identities.contains(new Pair<>("conference", "irc"))) {
					return cursor.getKey().toString();
				}
			}
		}
		return null;
	}

	public int getTimeToNextAttempt() {
		final int interval = (int) (25 * Math.pow(1.5, attempt));
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

	private class Info {
		public final ArrayList<String> features = new ArrayList<>();
		public final ArrayList<Pair<String,String>> identities = new ArrayList<>();
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
						connection.disco.get(server).features.contains(feature);
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
				final Pair<String, String> needle = new Pair<>("pubsub", "pep");
				Info info = disco.get(account.getServer());
				if (info != null && info.identities.contains(needle)) {
					return true;
				} else {
					info = disco.get(account.getJid().toBareJid());
					return info != null && info.identities.contains(needle);
				}
			}
		}

		public boolean mam() {
			if (hasDiscoFeature(account.getJid().toBareJid(), "urn:xmpp:mam:0")) {
				return true;
			} else {
				return hasDiscoFeature(account.getServer(), "urn:xmpp:mam:0");
			}
		}

		public boolean advancedStreamFeaturesLoaded() {
			synchronized (XmppConnection.this.disco) {
				return disco.containsKey(account.getServer());
			}
		}

		public boolean rosterVersioning() {
			return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
		}

		public void setBlockListRequested(boolean value) {
			this.blockListRequested = value;
		}

		public boolean httpUpload() {
			return !Config.DISABLE_HTTP_UPLOAD && findDiscoItemsByFeature(Xmlns.HTTP_UPLOAD).size() > 0;
		}
	}

	private IqGenerator getIqGenerator() {
		return mXmppConnectionService.getIqGenerator();
	}
}
