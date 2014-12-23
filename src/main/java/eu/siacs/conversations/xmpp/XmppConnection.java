package eu.siacs.conversations.xmpp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import org.apache.http.conn.ssl.StrictHostnameVerifier;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.sasl.DigestMd5;
import eu.siacs.conversations.crypto.sasl.Plain;
import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import eu.siacs.conversations.crypto.sasl.ScramSha1;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.DNSHelper;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
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
	private final Context applicationContext;
	protected Account account;
	private final WakeLock wakeLock;
	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter;
	private final Features features = new Features(this);
	private boolean shouldBind = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	private final HashMap<String, List<String>> disco = new HashMap<>();

	private String streamId = null;
	private int smVersion = 3;
	private final SparseArray<String> messageReceipts = new SparseArray<>();

	private boolean enabledEncryption = false;
	private boolean enabledCarbons = false;

	private int stanzasReceived = 0;
	private int stanzasSent = 0;
	private long lastPaketReceived = 0;
	private long lastPingSent = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private int attempt = 0;
	private final Map<String, PacketReceived> packetCallbacks = new Hashtable<>();
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

	public XmppConnection(final Account account, final XmppConnectionService service) {
		this.account = account;
		this.wakeLock = service.getPowerManager().newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, account.getJid().toBareJid().toString());
		tagWriter = new TagWriter();
		mXmppConnectionService = service;
		applicationContext = service.getApplicationContext();
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
		enabledEncryption = false;
		lastConnect = SystemClock.elapsedRealtime();
		lastPingSent = SystemClock.elapsedRealtime();
		this.attempt++;
		try {
			shouldAuthenticate = shouldBind = !account
				.isOptionSet(Account.OPTION_REGISTER);
			tagReader = new XmlReader(wakeLock);
			tagWriter = new TagWriter();
			packetCallbacks.clear();
			this.changeStatus(Account.State.CONNECTING);
			final Bundle result = DNSHelper.getSRVRecord(account.getServer());
			final ArrayList<Parcelable> values = result.getParcelableArrayList("values");
			if ("timeout".equals(result.getString("error"))) {
				throw new IOException("timeout in dns");
			} else if (values != null) {
				int i = 0;
				boolean socketError = true;
				while (socketError && values.size() > i) {
					final Bundle namePort = (Bundle) values.get(i);
					try {
						String srvRecordServer;
						try {
							srvRecordServer=IDN.toASCII(namePort.getString("name"));
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
						socket.connect(addr, 20000);
						socketError = false;
					} catch (final UnknownHostException e) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage());
						i++;
					} catch (final IOException e) {
						Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage());
						i++;
					}
				}
				if (socketError) {
					throw new UnknownHostException();
				}
			} else if (result.containsKey("error")
					&& "nosrv".equals(result.getString("error", null))) {
				socket = new Socket(account.getServer().getDomainpart(), 5222);
			} else {
				throw new IOException("timeout in dns");
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
					processStream(nextTag);
					break;
				} else {
					throw new IOException("unknown tag on connect");
				}
			}
			if (socket.isConnected()) {
				socket.close();
			}
		} catch (final UnknownHostException | ConnectException e) {
			this.changeStatus(Account.State.SERVER_NOT_FOUND);
		} catch (final IOException | XmlPullParserException | NoSuchAlgorithmException e) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": " + e.getMessage());
			this.changeStatus(Account.State.OFFLINE);
		} finally {
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
		connect();
	}

	private void processStream(final Tag currentTag) throws XmlPullParserException,
					IOException, NoSuchAlgorithmException {
						Tag nextTag = tagReader.readTag();

						while ((nextTag != null) && (!nextTag.isEnd("stream"))) {
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
								processStream(tagReader.readTag());
								break;
							} else if (nextTag.isStart("failure")) {
								tagReader.readElement(nextTag);
								changeStatus(Account.State.UNAUTHORIZED);
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
											+ ": stream managment(" + smVersion + ") enabled");
								}
								this.lastSessionStarted = SystemClock.elapsedRealtime();
								this.stanzasReceived = 0;
								final RequestPacket r = new RequestPacket(smVersion);
								tagWriter.writeStanzaAsync(r);
							} else if (nextTag.isStart("resumed")) {
								lastPaketReceived = SystemClock.elapsedRealtime();
								final Element resumed = tagReader.readElement(nextTag);
								final String h = resumed.getAttribute("h");
								try {
									final int serverCount = Integer.parseInt(h);
									if (serverCount != stanzasSent) {
										Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
												+ ": session resumed with lost packages");
										stanzasSent = serverCount;
									} else {
										Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
												+ ": session resumed");
									}
									if (acknowledgedListener != null) {
										for (int i = 0; i < messageReceipts.size(); ++i) {
											if (serverCount >= messageReceipts.keyAt(i)) {
												acknowledgedListener.onMessageAcknowledged(
														account, messageReceipts.valueAt(i));
											}
										}
									}
									messageReceipts.clear();
								} catch (final NumberFormatException ignored) {
								}
								sendServiceDiscoveryInfo(account.getServer());
								sendServiceDiscoveryItems(account.getServer());
								sendInitialPing();
							} else if (nextTag.isStart("r")) {
								tagReader.readElement(nextTag);
								final AckPacket ack = new AckPacket(this.stanzasReceived, smVersion);
								tagWriter.writeStanzaAsync(ack);
							} else if (nextTag.isStart("a")) {
								final Element ack = tagReader.readElement(nextTag);
								lastPaketReceived = SystemClock.elapsedRealtime();
								final int serverSequence = Integer.parseInt(ack.getAttribute("h"));
								final String msgId = this.messageReceipts.get(serverSequence);
								if (msgId != null) {
									if (this.acknowledgedListener != null) {
										this.acknowledgedListener.onMessageAcknowledged(
												account, msgId);
									}
									this.messageReceipts.remove(serverSequence);
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
						if (account.getStatus() == Account.State.ONLINE) {
							account. setStatus(Account.State.OFFLINE);
							if (statusListener != null) {
								statusListener.onStatusChanged(account);
							}
						}
	}

	private void sendInitialPing() {
		Log.d(Config.LOGTAG, account.getJid().toBareJid().toString() + ": sending intial ping");
		final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setFrom(account.getJid());
		iq.addChild("ping", "urn:xmpp:ping");
		this.sendIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid().toString()
						+ ": online with resource " + account.getResource());
				changeStatus(Account.State.ONLINE);
			}
		});
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
		++stanzasReceived;
		lastPaketReceived = SystemClock.elapsedRealtime();
		return element;
	}

	private void processIq(final Tag currentTag) throws XmlPullParserException,
					IOException {
						final IqPacket packet = (IqPacket) processPacket(currentTag, PACKET_IQ);

						if (packet.getId() == null) {
							return; // an iq packet without id is definitely invalid
						}

						if (packet instanceof JinglePacket) {
							if (this.jingleListener != null) {
								this.jingleListener.onJinglePacketReceived(account,
										(JinglePacket) packet);
							}
						} else {
							if (packetCallbacks.containsKey(packet.getId())) {
								if (packetCallbacks.get(packet.getId()) instanceof OnIqPacketReceived) {
									((OnIqPacketReceived) packetCallbacks.get(packet.getId()))
										.onIqPacketReceived(account, packet);
								}

								packetCallbacks.remove(packet.getId());
							} else if ((packet.getType() == IqPacket.TYPE_GET || packet
										.getType() == IqPacket.TYPE_SET)
									&& this.unregisteredIqListener != null) {
								this.unregisteredIqListener.onIqPacketReceived(account, packet);
									}
						}
	}

	private void processMessage(final Tag currentTag) throws XmlPullParserException,
					IOException {
						final MessagePacket packet = (MessagePacket) processPacket(currentTag,
								PACKET_MESSAGE);
						final String id = packet.getAttribute("id");
						if ((id != null) && (packetCallbacks.containsKey(id))) {
							if (packetCallbacks.get(id) instanceof OnMessagePacketReceived) {
								((OnMessagePacketReceived) packetCallbacks.get(id))
									.onMessagePacketReceived(account, packet);
							}
							packetCallbacks.remove(id);
						} else if (this.messageListener != null) {
							this.messageListener.onMessagePacketReceived(account, packet);
						}
	}

	private void processPresence(final Tag currentTag) throws XmlPullParserException,
					IOException {
						PresencePacket packet = (PresencePacket) processPacket(currentTag,
								PACKET_PRESENCE);
						final String id = packet.getAttribute("id");
						if ((id != null) && (packetCallbacks.containsKey(id))) {
							if (packetCallbacks.get(id) instanceof OnPresencePacketReceived) {
								((OnPresencePacketReceived) packetCallbacks.get(id))
									.onPresencePacketReceived(account, packet);
							}
							packetCallbacks.remove(id);
						} else if (this.presenceListener != null) {
							this.presenceListener.onPresencePacketReceived(account, packet);
						}
	}

	private void sendStartTLS() throws IOException {
		final Tag startTLS = Tag.empty("starttls");
		startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
		tagWriter.writeTag(startTLS);
	}

	private SharedPreferences getPreferences() {
		return PreferenceManager
			.getDefaultSharedPreferences(applicationContext);
	}

	private boolean enableLegacySSL() {
		return getPreferences().getBoolean("enable_legacy_ssl", false);
	}

	private void switchOverToTls(final Tag currentTag) throws XmlPullParserException,
					IOException {
						tagReader.readTag();
						try {
							final SSLContext sc = SSLContext.getInstance("TLS");
							sc.init(null,
									new X509TrustManager[]{this.mXmppConnectionService.getMemorizingTrustManager()},
									mXmppConnectionService.getRNG());
							final SSLSocketFactory factory = sc.getSocketFactory();

							if (factory == null) {
								throw new IOException("SSLSocketFactory was null");
							}

							final HostnameVerifier verifier = this.mXmppConnectionService.getMemorizingTrustManager().wrapHostnameVerifier(new StrictHostnameVerifier());

							if (socket == null || socket.isClosed()) {
								throw new IOException("socket null or closed");
							}
							final InetAddress address = socket.getInetAddress();
							if (address == null) {
								throw new IOException("socket address was null");
							}

							final SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket,address.getHostAddress(), socket.getPort(),true);

							// Support all protocols except legacy SSL.
							// The min SDK version prevents us having to worry about SSLv2. In
							// future, this may be true of SSLv3 as well.
							final String[] supportProtocols;
							if (enableLegacySSL()) {
								supportProtocols = sslSocket.getSupportedProtocols();
							} else {
								final Collection<String> supportedProtocols = new LinkedList<>(
										Arrays.asList(sslSocket.getSupportedProtocols()));
								supportedProtocols.remove("SSLv3");
								supportProtocols = new String[supportedProtocols.size()];
								supportedProtocols.toArray(supportProtocols);
							}
							sslSocket.setEnabledProtocols(supportProtocols);

							if (verifier != null
									&& !verifier.verify(account.getServer().getDomainpart(),
										sslSocket.getSession())) {
								Log.d(Config.LOGTAG,account.getJid().toBareJid()+": TLS certificate verification failed");
								disconnect(true);
								changeStatus(Account.State.SECURITY_ERROR);
									}
							tagReader.setInputStream(sslSocket.getInputStream());
							tagWriter.setOutputStream(sslSocket.getOutputStream());
							sendStartStream();
							Log.d(Config.LOGTAG, account.getJid().toBareJid()
									+ ": TLS connection established");
							enabledEncryption = true;
							processStream(tagReader.readTag());
							sslSocket.close();
						} catch (final NoSuchAlgorithmException | KeyManagementException e1) {
							e1.printStackTrace();
						}
	}

	private void processStreamFeatures(final Tag currentTag)
		throws XmlPullParserException, IOException {
		this.streamFeatures = tagReader.readElement(currentTag);
		if (this.streamFeatures.hasChild("starttls") && !enabledEncryption) {
			sendStartTLS();
		} else if (this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)
				&& enabledEncryption) {
			sendRegistryRequest();
		} else if (!this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)) {
			changeStatus(Account.State.REGISTRATION_NOT_SUPPORTED);
			disconnect(true);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate && enabledEncryption) {
			final List<String> mechanisms = extractMechanisms(streamFeatures
					.findChild("mechanisms"));
			final Element auth = new Element("auth");
			auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
			if (mechanisms.contains("SCRAM-SHA-1")) {
				saslMechanism = new ScramSha1(tagWriter, account, mXmppConnectionService.getRNG());
			} else if (mechanisms.contains("DIGEST-MD5")) {
				saslMechanism = new DigestMd5(tagWriter, account, mXmppConnectionService.getRNG());
			} else if (mechanisms.contains("PLAIN")) {
				saslMechanism = new Plain(tagWriter, account);
			}
			final JSONObject keys = account.getKeys();
			try {
				if (keys.has(Account.PINNED_MECHANISM_KEY) &&
						keys.getInt(Account.PINNED_MECHANISM_KEY) > saslMechanism.getPriority() ) {
					Log.e(Config.LOGTAG, "Auth failed. Authentication mechanism " + saslMechanism.getMechanism() +
							" has lower priority (" + String.valueOf(saslMechanism.getPriority()) +
							") than pinned priority (" + keys.getInt(Account.PINNED_MECHANISM_KEY) +
							"). Possible downgrade attack?");
					disconnect(true);
					changeStatus(Account.State.SECURITY_ERROR);
						}
			} catch (final JSONException e) {
				Log.d(Config.LOGTAG, "Parse error while checking pinned auth mechanism");
			}
			Log.d(Config.LOGTAG,account.getJid().toString()+": Authenticating with " + saslMechanism.getMechanism());
			auth.setAttribute("mechanism", saslMechanism.getMechanism());
			if (!saslMechanism.getClientFirstMessage().isEmpty()) {
				auth.setContent(saslMechanism.getClientFirstMessage());
			}
			tagWriter.writeElement(auth);
		} else if (this.streamFeatures.hasChild("sm", "urn:xmpp:sm:"
					+ smVersion)
				&& streamId != null) {
			final ResumePacket resume = new ResumePacket(this.streamId,
					stanzasReceived, smVersion);
			this.tagWriter.writeStanzaAsync(resume);
		} else if (this.streamFeatures.hasChild("bind") && shouldBind) {
			sendBindRequest();
		} else {
			disconnect(true);
			changeStatus(Account.State.INCOMPATIBLE_SERVER);
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
		final IqPacket register = new IqPacket(IqPacket.TYPE_GET);
		register.query("jabber:iq:register");
		register.setTo(account.getServer());
		sendIqPacket(register, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				final Element instructions = packet.query().findChild("instructions");
				if (packet.query().hasChild("username")
						&& (packet.query().hasChild("password"))) {
					final IqPacket register = new IqPacket(IqPacket.TYPE_SET);
					final Element username = new Element("username")
						.setContent(account.getUsername());
					final Element password = new Element("password")
						.setContent(account.getPassword());
					register.query("jabber:iq:register").addChild(username);
					register.query().addChild(password);
					sendIqPacket(register, new OnIqPacketReceived() {

						@Override
						public void onIqPacketReceived(final Account account, final IqPacket packet) {
							if (packet.getType() == IqPacket.TYPE_RESULT) {
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
					});
				} else {
					changeStatus(Account.State.REGISTRATION_FAILED);
					disconnect(true);
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": could not register. instructions are"
							+ instructions.getContent());
				}
			}
		});
	}

	private void sendBindRequest() {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")
			.addChild("resource").setContent(account.getResource());
		this.sendUnboundIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				final Element bind = packet.findChild("bind");
				if (bind != null) {
					final Element jid = bind.findChild("jid");
					if (jid != null && jid.getContent() != null) {
						try {
							account.setResource(Jid.fromString(jid.getContent()).getResourcepart());
						} catch (final InvalidJidException e) {
							// TODO: Handle the case where an external JID is technically invalid?
						}
						if (streamFeatures.hasChild("sm", "urn:xmpp:sm:3")) {
							smVersion = 3;
							final EnablePacket enable = new EnablePacket(smVersion);
							tagWriter.writeStanzaAsync(enable);
							stanzasSent = 0;
							messageReceipts.clear();
						} else if (streamFeatures.hasChild("sm",
									"urn:xmpp:sm:2")) {
							smVersion = 2;
							final EnablePacket enable = new EnablePacket(smVersion);
							tagWriter.writeStanzaAsync(enable);
							stanzasSent = 0;
							messageReceipts.clear();
						}
						enabledCarbons = false;
						disco.clear();
						sendServiceDiscoveryInfo(account.getServer());
						sendServiceDiscoveryItems(account.getServer());
						if (bindListener != null) {
							bindListener.onBind(account);
						}
						sendInitialPing();
					} else {
						disconnect(true);
					}
				} else {
					disconnect(true);
				}
			}
		});
		if (this.streamFeatures.hasChild("session")) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid()
					+ ": sending deprecated session");
			final IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
			startSession.addChild("session",
					"urn:ietf:params:xml:ns:xmpp-session");
			this.sendUnboundIqPacket(startSession, null);
		}
	}

	private void sendServiceDiscoveryInfo(final Jid server) {
		if (disco.containsKey(server.toDomainJid().toString())) {
			if (account.getServer().equals(server.toDomainJid())) {
				enableAdvancedStreamFeatures();
			}
		} else {
			final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
			iq.setTo(server.toDomainJid());
			iq.query("http://jabber.org/protocol/disco#info");
			this.sendIqPacket(iq, new OnIqPacketReceived() {

				@Override
				public void onIqPacketReceived(final Account account, final IqPacket packet) {
					final List<Element> elements = packet.query().getChildren();
					final List<String> features = new ArrayList<>();
					for (final Element element : elements) {
						if (element.getName().equals("identity")) {
							if ("irc".equals(element.getAttribute("type"))) {
								//add fake feature to not confuse irc and real muc
								features.add("siacs:no:muc");
							}
						} else if (element.getName().equals("feature")) {
							features.add(element.getAttribute("var"));
						}
					}
					disco.put(server.toDomainJid().toString(), features);

					if (account.getServer().equals(server.toDomainJid())) {
						enableAdvancedStreamFeatures();
						for (final OnAdvancedStreamFeaturesLoaded listener : advancedStreamFeaturesLoadedListeners) {
							listener.onAdvancedStreamFeaturesAvailable(account);
						}
					}
				}
			});
		}
	}

	private void enableAdvancedStreamFeatures() {
		if (getFeatures().carbons()) {
			if (!enabledCarbons) {
				sendEnableCarbons();
			}
		}
		if (getFeatures().blocking()) {
			Log.d(Config.LOGTAG, "Requesting block list");
			this.sendIqPacket(getIqGenerator().generateGetBlockList(), mXmppConnectionService.getIqParser());
		}
	}

	private void sendServiceDiscoveryItems(final Jid server) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setTo(server.toDomainJid());
		iq.query("http://jabber.org/protocol/disco#items");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				final List<Element> elements = packet.query().getChildren();
				for (final Element element : elements) {
					if (element.getName().equals("item")) {
						final Jid jid = element.getAttributeAsJid("jid");
						if (jid != null && !jid.equals(account.getServer())) {
							sendServiceDiscoveryInfo(jid);
						}
					}
				}
			}
		});
	}

	private void sendEnableCarbons() {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("enable", "urn:xmpp:carbons:2");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(final Account account, final IqPacket packet) {
				if (!packet.hasChild("error")) {
					Log.d(Config.LOGTAG, account.getJid().toBareJid()
							+ ": successfully enabled carbons");
					enabledCarbons = true;
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
		}
	}

	private void sendStartStream() throws IOException {
		final Tag stream = Tag.start("stream:stream");
		stream.setAttribute("from", account.getJid().toBareJid().toString());
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

	public void sendIqPacket(final IqPacket packet, final PacketReceived callback) {
		if (packet.getId() == null) {
			final String id = nextRandomId();
			packet.setAttribute("id", id);
		}
		packet.setFrom(account.getJid());
		this.sendPacket(packet, callback);
	}

	public void sendUnboundIqPacket(final IqPacket packet, final PacketReceived callback) {
		if (packet.getId() == null) {
			final String id = nextRandomId();
			packet.setAttribute("id", id);
		}
		this.sendPacket(packet, callback);
	}

	public void sendMessagePacket(final MessagePacket packet) {
		this.sendPacket(packet, null);
	}

	public void sendPresencePacket(final PresencePacket packet) {
		this.sendPacket(packet, null);
	}

	private synchronized void sendPacket(final AbstractStanza packet, final PacketReceived callback) {
		if (packet.getName().equals("iq") || packet.getName().equals("message")
				|| packet.getName().equals("presence")) {
			++stanzasSent;
				}
		tagWriter.writeStanzaAsync(packet);
		if (packet instanceof MessagePacket && packet.getId() != null
				&& this.streamId != null) {
			Log.d(Config.LOGTAG, "request delivery report for stanza "
					+ stanzasSent);
			this.messageReceipts.put(stanzasSent, packet.getId());
			tagWriter.writeStanzaAsync(new RequestPacket(this.smVersion));
				}
		if (callback != null) {
			if (packet.getId() == null) {
				packet.setId(nextRandomId());
			}
			packetCallbacks.put(packet.getId(), callback);
		}
	}

	public void sendPing() {
		if (streamFeatures.hasChild("sm")) {
			tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
		} else {
			final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
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
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": disconnecting");
		try {
			if (force) {
				socket.close();
				return;
			}
			new Thread(new Runnable() {

				@Override
				public void run() {
					if (tagWriter.isActive()) {
						tagWriter.finish();
						try {
							while (!tagWriter.finished()) {
								Log.d(Config.LOGTAG, "not yet finished");
								Thread.sleep(100);
							}
							tagWriter.writeTag(Tag.end("stream:stream"));
							socket.close();
						} catch (final IOException e) {
							Log.d(Config.LOGTAG,
									"io exception during disconnect");
						} catch (final InterruptedException e) {
							Log.d(Config.LOGTAG, "interrupted");
						}
					}
				}
			}).start();
		} catch (final IOException e) {
			Log.d(Config.LOGTAG, "io exception during disconnect");
		}
	}

	public List<String> findDiscoItemsByFeature(final String feature) {
		final List<String> items = new ArrayList<>();
		for (final Entry<String, List<String>> cursor : disco.entrySet()) {
			if (cursor.getValue().contains(feature)) {
				items.add(cursor.getKey());
			}
		}
		return items;
	}

	public String findDiscoItemByFeature(final String feature) {
		final List<String> items = findDiscoItemsByFeature(feature);
		if (items.size() >= 1) {
			return items.get(0);
		}
		return null;
	}

	public void r() {
		this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
	}

	public String getMucServer() {
		for (final Entry<String, List<String>> cursor : disco.entrySet()) {
			final List<String> value = cursor.getValue();
			if (value.contains("http://jabber.org/protocol/muc") && !value.contains("jabber:iq:gateway") && !value.contains("siacs:no:muc")) {
				return cursor.getKey();
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
		final long diff;
		if (this.lastSessionStarted == 0) {
			diff = SystemClock.elapsedRealtime() - this.lastConnect;
		} else {
			diff = SystemClock.elapsedRealtime() - this.lastSessionStarted;
		}
		return System.currentTimeMillis() - diff;
	}

	public long getLastConnect() {
		return this.lastConnect;
	}

	public long getLastPingSent() {
		return this.lastPingSent;
	}

	public long getLastPacketReceived() {
		return this.lastPaketReceived;
	}

	public void sendActive() {
		this.sendPacket(new ActivePacket(), null);
	}

	public void sendInactive() {
		this.sendPacket(new InactivePacket(), null);
	}

	public class Features {
		XmppConnection connection;

		public Features(final XmppConnection connection) {
			this.connection = connection;
		}

		private boolean hasDiscoFeature(final Jid server, final String feature) {
			return connection.disco.containsKey(server.toDomainJid().toString()) &&
				connection.disco.get(server.toDomainJid().toString()).contains(feature);
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
			return streamId != null;
		}

		public boolean csi() {
			return connection.streamFeatures != null && connection.streamFeatures.hasChild("csi", "urn:xmpp:csi:0");
		}

		public boolean pubsub() {
			return hasDiscoFeature(account.getServer(),
					"http://jabber.org/protocol/pubsub#publish");
		}

		public boolean mam() {
			return hasDiscoFeature(account.getServer(), "urn:xmpp:mam:0");
		}

		public boolean advancedStreamFeaturesLoaded() {
			return disco.containsKey(account.getServer().toString());
		}

		public boolean rosterVersioning() {
			return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
		}

		public boolean streamhost() {
			return connection
				.findDiscoItemByFeature("http://jabber.org/protocol/bytestreams") != null;
		}
	}

	private IqGenerator getIqGenerator() {
		return mXmppConnectionService.getIqGenerator();
	}
}
