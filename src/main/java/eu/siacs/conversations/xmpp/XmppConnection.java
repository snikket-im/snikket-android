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
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.IDN;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.DNSHelper;
import eu.siacs.conversations.utils.zlib.ZLibInputStream;
import eu.siacs.conversations.utils.zlib.ZLibOutputStream;
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
	private WakeLock wakeLock;
	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter;
	private Features features = new Features(this);
	private boolean shouldBind = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	private HashMap<String, List<String>> disco = new HashMap<>();

	private String streamId = null;
	private int smVersion = 3;
	private SparseArray<String> messageReceipts = new SparseArray<>();

	private boolean usingCompression = false;
	private boolean usingEncryption = false;
	private int stanzasReceived = 0;
	private int stanzasSent = 0;
	private long lastPaketReceived = 0;
	private long lastPingSent = 0;
	private long lastConnect = 0;
	private long lastSessionStarted = 0;
	private int attempt = 0;
	private Hashtable<String, PacketReceived> packetCallbacks = new Hashtable<>();
	private OnPresencePacketReceived presenceListener = null;
	private OnJinglePacketReceived jingleListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;
	private OnMessagePacketReceived messageListener = null;
	private OnStatusChanged statusListener = null;
	private OnBindListener bindListener = null;
	private OnMessageAcknowledged acknowledgedListener = null;
	private XmppConnectionService mXmppConnectionService = null;

	public XmppConnection(Account account, XmppConnectionService service) {
		this.account = account;
		this.wakeLock = service.getPowerManager().newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, account.getJid().toString());
		tagWriter = new TagWriter();
		mXmppConnectionService = service;
		applicationContext = service.getApplicationContext();
	}

	protected void changeStatus(int nextStatus) {
		if (account.getStatus() != nextStatus) {
			if ((nextStatus == Account.STATUS_OFFLINE)
					&& (account.getStatus() != Account.STATUS_CONNECTING)
					&& (account.getStatus() != Account.STATUS_ONLINE)
					&& (account.getStatus() != Account.STATUS_DISABLED)) {
				return;
			}
			if (nextStatus == Account.STATUS_ONLINE) {
				this.attempt = 0;
			}
			account.setStatus(nextStatus);
			if (statusListener != null) {
				statusListener.onStatusChanged(account);
			}
		}
	}

	protected void connect() {
		Log.d(Config.LOGTAG, account.getJid().toString() + ": connecting");
		usingCompression = false;
		usingEncryption = false;
		lastConnect = SystemClock.elapsedRealtime();
		lastPingSent = SystemClock.elapsedRealtime();
		this.attempt++;
		try {
			shouldAuthenticate = shouldBind = !account
					.isOptionSet(Account.OPTION_REGISTER);
			tagReader = new XmlReader(wakeLock);
			tagWriter = new TagWriter();
			packetCallbacks.clear();
			this.changeStatus(Account.STATUS_CONNECTING);
			Bundle result = DNSHelper.getSRVRecord(account.getServer());
			ArrayList<Parcelable> values = result.getParcelableArrayList("values");
			if ("timeout".equals(result.getString("error"))) {
				Log.d(Config.LOGTAG, account.getJid().toString() + ": dns timeout");
				this.changeStatus(Account.STATUS_OFFLINE);
				return;
			} else if (values != null) {
				int i = 0;
				boolean socketError = true;
				while (socketError && values.size() > i) {
					Bundle namePort = (Bundle) values.get(i);
					try {
						String srvRecordServer;
                        try {
                            srvRecordServer=IDN.toASCII(namePort.getString("name"));
                        } catch (final IllegalArgumentException e) {
                            // TODO: Handle me?`
                            srvRecordServer = "";
                        }
						int srvRecordPort = namePort.getInt("port");
						String srvIpServer = namePort.getString("ipv4");
						InetSocketAddress addr;
						if (srvIpServer != null) {
							addr = new InetSocketAddress(srvIpServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toString()
									+ ": using values from dns " + srvRecordServer
									+ "[" + srvIpServer + "]:" + srvRecordPort);
						} else {
							addr = new InetSocketAddress(srvRecordServer, srvRecordPort);
							Log.d(Config.LOGTAG, account.getJid().toString()
									+ ": using values from dns "
									+ srvRecordServer + ":" + srvRecordPort);
						}
						socket = new Socket();
						socket.connect(addr, 20000);
						socketError = false;
					} catch (UnknownHostException e) {
						Log.d(Config.LOGTAG, account.getJid().toString() + ": " + e.getMessage());
						i++;
					} catch (IOException e) {
						Log.d(Config.LOGTAG, account.getJid().toString() + ": " + e.getMessage());
						i++;
					}
				}
				if (socketError) {
					this.changeStatus(Account.STATUS_SERVER_NOT_FOUND);
					if (wakeLock.isHeld()) {
						try {
							wakeLock.release();
						} catch (final RuntimeException ignored) {
						}
					}
					return;
				}
			} else if (result.containsKey("error")
					&& "nosrv".equals(result.getString("error", null))) {
				socket = new Socket(account.getServer().getDomainpart(), 5222);
			} else {
				Log.d(Config.LOGTAG, account.getJid().toString()
						+ ": timeout in DNS resolution");
				changeStatus(Account.STATUS_OFFLINE);
				return;
			}
			OutputStream out = socket.getOutputStream();
			tagWriter.setOutputStream(out);
			InputStream in = socket.getInputStream();
			tagReader.setInputStream(in);
			tagWriter.beginDocument();
			sendStartStream();
			Tag nextTag;
			while ((nextTag = tagReader.readTag()) != null) {
				if (nextTag.isStart("stream")) {
					processStream(nextTag);
					break;
				} else {
					Log.d(Config.LOGTAG,
							"found unexpected tag: " + nextTag.getName());
					return;
				}
			}
			if (socket.isConnected()) {
				socket.close();
			}
		} catch (UnknownHostException e) {
			this.changeStatus(Account.STATUS_SERVER_NOT_FOUND);
			if (wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (final RuntimeException ignored) {
				}
			}
        } catch (final IOException | XmlPullParserException e) {
			Log.d(Config.LOGTAG, account.getJid().toString() + ": " + e.getMessage());
			this.changeStatus(Account.STATUS_OFFLINE);
			if (wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (final RuntimeException ignored) {
				}
			}
        } catch (NoSuchAlgorithmException e) {
			Log.d(Config.LOGTAG, account.getJid().toString() + ": " + e.getMessage());
			this.changeStatus(Account.STATUS_OFFLINE);
			Log.d(Config.LOGTAG, "compression exception " + e.getMessage());
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
			} else if (nextTag.isStart("compressed")) {
				switchOverToZLib(nextTag);
			} else if (nextTag.isStart("success")) {
				Log.d(Config.LOGTAG, account.getJid().toString() + ": logged in");
				tagReader.readTag();
				tagReader.reset();
				sendStartStream();
				processStream(tagReader.readTag());
				break;
			} else if (nextTag.isStart("failure")) {
				tagReader.readElement(nextTag);
				changeStatus(Account.STATUS_UNAUTHORIZED);
			} else if (nextTag.isStart("challenge")) {
				String challange = tagReader.readElement(nextTag).getContent();
				Element response = new Element("response");
				response.setAttribute("xmlns",
						"urn:ietf:params:xml:ns:xmpp-sasl");
				response.setContent(CryptoHelper.saslDigestMd5(account,
						challange, mXmppConnectionService.getRNG()));
				tagWriter.writeElement(response);
			} else if (nextTag.isStart("enabled")) {
				Element enabled = tagReader.readElement(nextTag);
				if ("true".equals(enabled.getAttribute("resume"))) {
					this.streamId = enabled.getAttribute("id");
					Log.d(Config.LOGTAG, account.getJid().toString()
							+ ": stream managment(" + smVersion
							+ ") enabled (resumable)");
				} else {
					Log.d(Config.LOGTAG, account.getJid().toString()
							+ ": stream managment(" + smVersion + ") enabled");
				}
				this.lastSessionStarted = SystemClock.elapsedRealtime();
				this.stanzasReceived = 0;
				RequestPacket r = new RequestPacket(smVersion);
				tagWriter.writeStanzaAsync(r);
			} else if (nextTag.isStart("resumed")) {
				lastPaketReceived = SystemClock.elapsedRealtime();
				Element resumed = tagReader.readElement(nextTag);
				String h = resumed.getAttribute("h");
				try {
					int serverCount = Integer.parseInt(h);
					if (serverCount != stanzasSent) {
						Log.d(Config.LOGTAG, account.getJid().toString()
								+ ": session resumed with lost packages");
						stanzasSent = serverCount;
					} else {
						Log.d(Config.LOGTAG, account.getJid().toString()
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
				sendInitialPing();

			} else if (nextTag.isStart("r")) {
				tagReader.readElement(nextTag);
				AckPacket ack = new AckPacket(this.stanzasReceived, smVersion);
				tagWriter.writeStanzaAsync(ack);
			} else if (nextTag.isStart("a")) {
				Element ack = tagReader.readElement(nextTag);
				lastPaketReceived = SystemClock.elapsedRealtime();
				int serverSequence = Integer.parseInt(ack.getAttribute("h"));
				String msgId = this.messageReceipts.get(serverSequence);
				if (msgId != null) {
					if (this.acknowledgedListener != null) {
						this.acknowledgedListener.onMessageAcknowledged(
								account, msgId);
					}
					this.messageReceipts.remove(serverSequence);
				}
			} else if (nextTag.isStart("failed")) {
				tagReader.readElement(nextTag);
				Log.d(Config.LOGTAG, account.getJid().toString() + ": resumption failed");
				streamId = null;
				if (account.getStatus() != Account.STATUS_ONLINE) {
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
		if (account.getStatus() == Account.STATUS_ONLINE) {
			account. setStatus(Account.STATUS_OFFLINE);
			if (statusListener != null) {
				statusListener.onStatusChanged(account);
			}
		}
	}

	private void sendInitialPing() {
		Log.d(Config.LOGTAG, account.getJid().toString() + ": sending intial ping");
		IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setFrom(account.getFullJid());
		iq.addChild("ping", "urn:xmpp:ping");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Log.d(Config.LOGTAG, account.getJid().toString()
						+ ": online with resource " + account.getResource());
				changeStatus(Account.STATUS_ONLINE);
			}
		});
	}

	private Element processPacket(Tag currentTag, int packetType)
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
				Element child = tagReader.readElement(nextTag);
				String type = currentTag.getAttribute("type");
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

	private void processIq(Tag currentTag) throws XmlPullParserException,
			IOException {
		IqPacket packet = (IqPacket) processPacket(currentTag, PACKET_IQ);

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

	private void processMessage(Tag currentTag) throws XmlPullParserException,
			IOException {
		MessagePacket packet = (MessagePacket) processPacket(currentTag,
				PACKET_MESSAGE);
		String id = packet.getAttribute("id");
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

	private void processPresence(Tag currentTag) throws XmlPullParserException,
			IOException {
		PresencePacket packet = (PresencePacket) processPacket(currentTag,
				PACKET_PRESENCE);
		String id = packet.getAttribute("id");
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

	private void sendCompressionZlib() throws IOException {
		Element compress = new Element("compress");
		compress.setAttribute("xmlns", "http://jabber.org/protocol/compress");
		compress.addChild("method").setContent("zlib");
		tagWriter.writeElement(compress);
	}

	private void switchOverToZLib(final Tag currentTag)
			throws XmlPullParserException, IOException,
			NoSuchAlgorithmException {
		tagReader.readTag(); // read tag close
		tagWriter.setOutputStream(new ZLibOutputStream(tagWriter
				.getOutputStream()));
		tagReader
				.setInputStream(new ZLibInputStream(tagReader.getInputStream()));

		sendStartStream();
		Log.d(Config.LOGTAG, account.getJid() + ": compression enabled");
		usingCompression = true;
		processStream(tagReader.readTag());
	}

	private void sendStartTLS() throws IOException {
		Tag startTLS = Tag.empty("starttls");
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
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null,
					new X509TrustManager[]{this.mXmppConnectionService.getMemorizingTrustManager()},
					mXmppConnectionService.getRNG());
			SSLSocketFactory factory = sc.getSocketFactory();

			if (factory == null) {
				throw new IOException("SSLSocketFactory was null");
			}

			final HostnameVerifier verifier = this.mXmppConnectionService.getMemorizingTrustManager().wrapHostnameVerifier(new StrictHostnameVerifier());

			if (socket == null) {
				throw new IOException("socket was null");
			}
			final SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket,
					socket.getInetAddress().getHostAddress(), socket.getPort(),
					true);

			// Support all protocols except legacy SSL.
			// The min SDK version prevents us having to worry about SSLv2. In
			// future, this may be true of SSLv3 as well.
			final String[] supportProtocols;
			if (enableLegacySSL()) {
				supportProtocols = sslSocket.getSupportedProtocols();
			} else {
				final List<String> supportedProtocols = new LinkedList<>(
						Arrays.asList(sslSocket.getSupportedProtocols()));
				supportedProtocols.remove("SSLv3");
				supportProtocols = new String[supportedProtocols.size()];
				supportedProtocols.toArray(supportProtocols);
			}
			sslSocket.setEnabledProtocols(supportProtocols);

			if (verifier != null
					&& !verifier.verify(account.getServer().getDomainpart(),
					sslSocket.getSession())) {
				sslSocket.close();
				throw new IOException("host mismatch in TLS connection");
			}
			tagReader.setInputStream(sslSocket.getInputStream());
			tagWriter.setOutputStream(sslSocket.getOutputStream());
			sendStartStream();
			Log.d(Config.LOGTAG, account.getJid()
					+ ": TLS connection established");
			usingEncryption = true;
			processStream(tagReader.readTag());
			sslSocket.close();
		} catch (final NoSuchAlgorithmException | KeyManagementException e1) {
			e1.printStackTrace();
		}
    }

	private void sendSaslAuthPlain() throws IOException {
		String saslString = CryptoHelper.saslPlain(account.getUsername(),
				account.getPassword());
		Element auth = new Element("auth");
		auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "PLAIN");
		auth.setContent(saslString);
		tagWriter.writeElement(auth);
	}

	private void sendSaslAuthDigestMd5() throws IOException {
		Element auth = new Element("auth");
		auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "DIGEST-MD5");
		tagWriter.writeElement(auth);
	}

	private void processStreamFeatures(Tag currentTag)
			throws XmlPullParserException, IOException {
		this.streamFeatures = tagReader.readElement(currentTag);
		if (this.streamFeatures.hasChild("starttls") && !usingEncryption) {
			sendStartTLS();
		} else if (compressionAvailable()) {
			sendCompressionZlib();
		} else if (this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)
				&& usingEncryption) {
			sendRegistryRequest();
		} else if (!this.streamFeatures.hasChild("register")
				&& account.isOptionSet(Account.OPTION_REGISTER)) {
			changeStatus(Account.STATUS_REGISTRATION_NOT_SUPPORTED);
			disconnect(true);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate && usingEncryption) {
			List<String> mechanisms = extractMechanisms(streamFeatures
					.findChild("mechanisms"));
			if (mechanisms.contains("PLAIN")) {
				sendSaslAuthPlain();
			} else if (mechanisms.contains("DIGEST-MD5")) {
				sendSaslAuthDigestMd5();
			}
		} else if (this.streamFeatures.hasChild("sm", "urn:xmpp:sm:"
				+ smVersion)
				&& streamId != null) {
			ResumePacket resume = new ResumePacket(this.streamId,
					stanzasReceived, smVersion);
			this.tagWriter.writeStanzaAsync(resume);
		} else if (this.streamFeatures.hasChild("bind") && shouldBind) {
			sendBindRequest();
		} else {
			Log.d(Config.LOGTAG, account.getJid()
					+ ": incompatible server. disconnecting");
			disconnect(true);
		}
	}

	private boolean compressionAvailable() {
		if (!this.streamFeatures.hasChild("compression",
				"http://jabber.org/features/compress"))
			return false;
		if (!ZLibOutputStream.SUPPORTED)
			return false;
		if (!account.isOptionSet(Account.OPTION_USECOMPRESSION))
			return false;

		Element compression = this.streamFeatures.findChild("compression",
				"http://jabber.org/features/compress");
		for (Element child : compression.getChildren()) {
			if (!"method".equals(child.getName()))
				continue;

			if ("zlib".equalsIgnoreCase(child.getContent())) {
				return true;
			}
		}
		return false;
	}

	private List<String> extractMechanisms(Element stream) {
		ArrayList<String> mechanisms = new ArrayList<>(stream
				.getChildren().size());
		for (Element child : stream.getChildren()) {
			mechanisms.add(child.getContent());
		}
		return mechanisms;
	}

	private void sendRegistryRequest() {
		IqPacket register = new IqPacket(IqPacket.TYPE_GET);
		register.query("jabber:iq:register");
		register.setTo(account.getServer());
		sendIqPacket(register, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Element instructions = packet.query().findChild("instructions");
				if (packet.query().hasChild("username")
						&& (packet.query().hasChild("password"))) {
					IqPacket register = new IqPacket(IqPacket.TYPE_SET);
					Element username = new Element("username")
							.setContent(account.getUsername());
					Element password = new Element("password")
							.setContent(account.getPassword());
					register.query("jabber:iq:register").addChild(username);
					register.query().addChild(password);
					sendIqPacket(register, new OnIqPacketReceived() {

						@Override
						public void onIqPacketReceived(Account account,
													   IqPacket packet) {
							if (packet.getType() == IqPacket.TYPE_RESULT) {
								account.setOption(Account.OPTION_REGISTER,
										false);
								changeStatus(Account.STATUS_REGISTRATION_SUCCESSFULL);
							} else if (packet.hasChild("error")
									&& (packet.findChild("error")
									.hasChild("conflict"))) {
								changeStatus(Account.STATUS_REGISTRATION_CONFLICT);
							} else {
								changeStatus(Account.STATUS_REGISTRATION_FAILED);
								Log.d(Config.LOGTAG, packet.toString());
							}
							disconnect(true);
						}
					});
				} else {
					changeStatus(Account.STATUS_REGISTRATION_FAILED);
					disconnect(true);
					Log.d(Config.LOGTAG, account.getJid()
							+ ": could not register. instructions are"
							+ instructions.getContent());
				}
			}
		});
	}

	private void sendBindRequest() throws IOException {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")
				.addChild("resource").setContent(account.getResource());
		this.sendUnboundIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				Element bind = packet.findChild("bind");
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
							EnablePacket enable = new EnablePacket(smVersion);
							tagWriter.writeStanzaAsync(enable);
							stanzasSent = 0;
							messageReceipts.clear();
						} else if (streamFeatures.hasChild("sm",
								"urn:xmpp:sm:2")) {
							smVersion = 2;
							EnablePacket enable = new EnablePacket(smVersion);
							tagWriter.writeStanzaAsync(enable);
							stanzasSent = 0;
							messageReceipts.clear();
						}
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
			Log.d(Config.LOGTAG, account.getJid()
					+ ": sending deprecated session");
			IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
			startSession.addChild("session",
					"urn:ietf:params:xml:ns:xmpp-session");
			this.sendUnboundIqPacket(startSession, null);
		}
	}

	private void sendServiceDiscoveryInfo(final Jid server) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setTo(server.toDomainJid());
		iq.query("http://jabber.org/protocol/disco#info");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				final List<Element> elements = packet.query().getChildren();
				final List<String> features = new ArrayList<>();
                for (Element element : elements) {
                    if (element.getName().equals("feature")) {
                        features.add(element.getAttribute("var"));
                    }
                }
				disco.put(server.toDomainJid().toString(), features);

				if (account.getServer().equals(server.toDomainJid())) {
					enableAdvancedStreamFeatures();
				}
			}
		});
	}

	private void enableAdvancedStreamFeatures() {
		if (getFeatures().carbons()) {
			sendEnableCarbons();
		}
	}

	private void sendServiceDiscoveryItems(final Jid server) {
		final IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setTo(server.toDomainJid());
		iq.query("http://jabber.org/protocol/disco#items");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				List<Element> elements = packet.query().getChildren();
                for (Element element : elements) {
                    if (element.getName().equals("item")) {
                        final String jid = element.getAttribute("jid");
                        try {
                            sendServiceDiscoveryInfo(Jid.fromString(jid).toDomainJid());
                        } catch (final InvalidJidException ignored) {
                            // TODO: Handle the case where an external JID is technically invalid?
                        }
                    }
                }
			}
		});
	}

	private void sendEnableCarbons() {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("enable", "urn:xmpp:carbons:2");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (!packet.hasChild("error")) {
					Log.d(Config.LOGTAG, account.getJid()
							+ ": successfully enabled carbons");
				} else {
					Log.d(Config.LOGTAG, account.getJid()
							+ ": error enableing carbons " + packet.toString());
				}
			}
		});
	}

	private void processStreamError(Tag currentTag)
			throws XmlPullParserException, IOException {
		Element streamError = tagReader.readElement(currentTag);
		if (streamError != null && streamError.hasChild("conflict")) {
			final String resource = account.getResource().split("\\.")[0];
            account.setResource(resource + "." + nextRandomId());
            Log.d(Config.LOGTAG,
					account.getJid() + ": switching resource due to conflict ("
							+ account.getResource() + ")");
		}
	}

	private void sendStartStream() throws IOException {
		Tag stream = Tag.start("stream:stream");
		stream.setAttribute("from", account.getJid().toString());
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

	public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
		if (packet.getId() == null) {
			String id = nextRandomId();
			packet.setAttribute("id", id);
		}
		packet.setFrom(account.getFullJid());
		this.sendPacket(packet, callback);
	}

	public void sendUnboundIqPacket(IqPacket packet, OnIqPacketReceived callback) {
		if (packet.getId() == null) {
			String id = nextRandomId();
			packet.setAttribute("id", id);
		}
		this.sendPacket(packet, callback);
	}

	public void sendMessagePacket(MessagePacket packet) {
		this.sendPacket(packet, null);
	}

	public void sendPresencePacket(PresencePacket packet) {
		this.sendPacket(packet, null);
	}

	private synchronized void sendPacket(final AbstractStanza packet,
										 PacketReceived callback) {
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
			IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
			iq.setFrom(account.getFullJid());
			iq.addChild("ping", "urn:xmpp:ping");
			this.sendIqPacket(iq, null);
		}
		this.lastPingSent = SystemClock.elapsedRealtime();
	}

	public void setOnMessagePacketReceivedListener(
			OnMessagePacketReceived listener) {
		this.messageListener = listener;
	}

	public void setOnUnregisteredIqPacketReceivedListener(
			OnIqPacketReceived listener) {
		this.unregisteredIqListener = listener;
	}

	public void setOnPresencePacketReceivedListener(
			OnPresencePacketReceived listener) {
		this.presenceListener = listener;
	}

	public void setOnJinglePacketReceivedListener(
			OnJinglePacketReceived listener) {
		this.jingleListener = listener;
	}

	public void setOnStatusChangedListener(OnStatusChanged listener) {
		this.statusListener = listener;
	}

	public void setOnBindListener(OnBindListener listener) {
		this.bindListener = listener;
	}

	public void setOnMessageAcknowledgeListener(OnMessageAcknowledged listener) {
		this.acknowledgedListener = listener;
	}

	public void disconnect(boolean force) {
		Log.d(Config.LOGTAG, account.getJid() + ": disconnecting");
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
						} catch (IOException e) {
							Log.d(Config.LOGTAG,
									"io exception during disconnect");
						} catch (InterruptedException e) {
							Log.d(Config.LOGTAG, "interrupted");
						}
					}
				}
			}).start();
		} catch (IOException e) {
			Log.d(Config.LOGTAG, "io exception during disconnect");
		}
	}

	public List<String> findDiscoItemsByFeature(String feature) {
		final List<String> items = new ArrayList<>();
		for (Entry<String, List<String>> cursor : disco.entrySet()) {
			if (cursor.getValue().contains(feature)) {
				items.add(cursor.getKey());
			}
		}
		return items;
	}

	public String findDiscoItemByFeature(String feature) {
		List<String> items = findDiscoItemsByFeature(feature);
		if (items.size() >= 1) {
			return items.get(0);
		}
		return null;
	}

	public void r() {
		this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
	}

	public String getMucServer() {
		return findDiscoItemByFeature("http://jabber.org/protocol/muc");
	}

	public int getTimeToNextAttempt() {
		int interval = (int) (25 * Math.pow(1.5, attempt));
		int secondsSinceLast = (int) ((SystemClock.elapsedRealtime() - this.lastConnect) / 1000);
		return interval - secondsSinceLast;
	}

	public int getAttempt() {
		return this.attempt;
	}

	public Features getFeatures() {
		return this.features;
	}

	public long getLastSessionEstablished() {
		long diff;
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

		public Features(XmppConnection connection) {
			this.connection = connection;
		}

		private boolean hasDiscoFeature(final Jid server, final String feature) {
            return connection.disco.containsKey(server.toDomainJid().toString()) &&
                    connection.disco.get(server.toDomainJid().toString()).contains(feature);
        }

		public boolean carbons() {
			return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
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

		public boolean rosterVersioning() {
            return connection.streamFeatures != null && connection.streamFeatures.hasChild("ver");
		}

		public boolean streamhost() {
			return connection
					.findDiscoItemByFeature("http://jabber.org/protocol/bytestreams") != null;
		}

		public boolean compression() {
			return connection.usingCompression;
		}
	}
}
