package eu.siacs.conversations.xmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.xmlpull.v1.XmlPullParserException;

import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.DNSHelper;
import eu.siacs.conversations.utils.SASL;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;

public class XmppConnection implements Runnable {

	protected Account account;
	private static final String LOGTAG = "xmppService";

	private PowerManager.WakeLock wakeLock;

	private SecureRandom random = new SecureRandom();

	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter;

	private boolean isTlsEncrypted = false;
	private boolean isAuthenticated = false;
	// private boolean shouldUseTLS = false;
	private boolean shouldConnect = true;
	private boolean shouldBind = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	private HashSet<String> discoFeatures = new HashSet<String>();

	private static final int PACKET_IQ = 0;
	private static final int PACKET_MESSAGE = 1;
	private static final int PACKET_PRESENCE = 2;

	private Hashtable<String, PacketReceived> packetCallbacks = new Hashtable<String, PacketReceived>();
	private OnPresencePacketReceived presenceListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;
	private OnMessagePacketReceived messageListener = null;
	private OnStatusChanged statusListener = null;

	public XmppConnection(Account account, PowerManager pm) {
		this.account = account;
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"XmppConnection");
		tagReader = new XmlReader(wakeLock);
		tagWriter = new TagWriter();
	}

	protected void changeStatus(int nextStatus) {
		account.setStatus(nextStatus);
		if (statusListener != null) {
			statusListener.onStatusChanged(account);
		}
	}

	protected void connect() {
		Log.d(LOGTAG, "connecting");
		try {
			this.changeStatus(Account.STATUS_CONNECTING);
			Bundle namePort = DNSHelper.getSRVRecord(account.getServer());
			String srvRecordServer = namePort.getString("name");
			int srvRecordPort = namePort.getInt("port");
			if (srvRecordServer != null) {
				Log.d(LOGTAG, account.getJid() + ": using values from dns "
						+ srvRecordServer + ":" + srvRecordPort);
				socket = new Socket(srvRecordServer, srvRecordPort);
			} else {
				socket = new Socket(account.getServer(), 5222);
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
					Log.d(LOGTAG, "found unexpected tag: " + nextTag.getName());
					return;
				}
			}
			if (socket.isConnected()) {
				socket.close();
			}
		} catch (UnknownHostException e) {
			this.changeStatus(Account.STATUS_SERVER_NOT_FOUND);
			if (wakeLock.isHeld()) {
				wakeLock.release();
			}
			return;
		} catch (IOException e) {
			this.changeStatus(Account.STATUS_OFFLINE);
			if (wakeLock.isHeld()) {
				wakeLock.release();
			}
			return;
		} catch (XmlPullParserException e) {
			this.changeStatus(Account.STATUS_OFFLINE);
			Log.d(LOGTAG, "xml exception " + e.getMessage());
			if (wakeLock.isHeld()) {
				wakeLock.release();
			}
			return;
		}

	}

	@Override
	public void run() {
		connect();
		Log.d(LOGTAG, "end run");
	}

	private void processStream(Tag currentTag) throws XmlPullParserException,
			IOException {
		Tag nextTag = tagReader.readTag();
		while ((nextTag != null) && (!nextTag.isEnd("stream"))) {
			if (nextTag.isStart("error")) {
				processStreamError(nextTag);
			} else if (nextTag.isStart("features")) {
				processStreamFeatures(nextTag);
				if ((streamFeatures.getChildren().size() == 1)
						&& (streamFeatures.hasChild("starttls"))
						&& (!account.isOptionSet(Account.OPTION_USETLS))) {
					changeStatus(Account.STATUS_SERVER_REQUIRES_TLS);
				}
			} else if (nextTag.isStart("proceed")) {
				switchOverToTls(nextTag);
			} else if (nextTag.isStart("success")) {
				isAuthenticated = true;
				Log.d(LOGTAG, account.getJid()
						+ ": read success tag in stream. reset again");
				tagReader.readTag();
				tagReader.reset();
				sendStartStream();
				processStream(tagReader.readTag());
				break;
			} else if (nextTag.isStart("failure")) {
				Element failure = tagReader.readElement(nextTag);
				changeStatus(Account.STATUS_UNAUTHORIZED);
			} else if (nextTag.isStart("iq")) {
				processIq(nextTag);
			} else if (nextTag.isStart("message")) {
				processMessage(nextTag);
			} else if (nextTag.isStart("presence")) {
				processPresence(nextTag);
			} else {
				Log.d(LOGTAG, "found unexpected tag: " + nextTag.getName()
						+ " as child of " + currentTag.getName());
			}
			nextTag = tagReader.readTag();
		}
		if (account.getStatus() == Account.STATUS_ONLINE) {
			account.setStatus(Account.STATUS_OFFLINE);
			if (statusListener != null) {
				statusListener.onStatusChanged(account);
			}
		}
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
		while (!nextTag.isEnd(element.getName())) {
			if (!nextTag.isNo()) {
				Element child = tagReader.readElement(nextTag);
				element.addChild(child);
			}
			nextTag = tagReader.readTag();
		}
		return element;
	}

	private void processIq(Tag currentTag) throws XmlPullParserException,
			IOException {
		IqPacket packet = (IqPacket) processPacket(currentTag, PACKET_IQ);
		if (packetCallbacks.containsKey(packet.getId())) {
			if (packetCallbacks.get(packet.getId()) instanceof OnIqPacketReceived) {
				((OnIqPacketReceived) packetCallbacks.get(packet.getId()))
						.onIqPacketReceived(account, packet);
			}

			packetCallbacks.remove(packet.getId());
		} else if (this.unregisteredIqListener != null) {
			this.unregisteredIqListener.onIqPacketReceived(account, packet);
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

	private void sendStartTLS() {
		Tag startTLS = Tag.empty("starttls");
		startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
		Log.d(LOGTAG, account.getJid() + ": sending starttls");
		tagWriter.writeTag(startTLS);
	}

	private void switchOverToTls(Tag currentTag) throws XmlPullParserException,
			IOException {
		Tag nextTag = tagReader.readTag(); // should be proceed end tag
		Log.d(LOGTAG, account.getJid() + ": now switch to ssl");
		try {
			SSLContext sc = SSLContext.getInstance("TLS");
			TrustManagerFactory tmf = TrustManagerFactory
					.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			// Initialise the TMF as you normally would, for example:
			// tmf.in
			try {
				tmf.init((KeyStore) null);
			} catch (KeyStoreException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			TrustManager[] trustManagers = tmf.getTrustManagers();
			final X509TrustManager origTrustmanager = (X509TrustManager) trustManagers[0];

			TrustManager[] wrappedTrustManagers = new TrustManager[] { new X509TrustManager() {

				@Override
				public void checkClientTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {
					origTrustmanager.checkClientTrusted(chain, authType);
				}

				@Override
				public void checkServerTrusted(X509Certificate[] chain,
						String authType) throws CertificateException {
					try {
						origTrustmanager.checkServerTrusted(chain, authType);
					} catch (CertificateException e) {
						Log.d(LOGTAG,"cert exeption");
					}
				}

				@Override
				public X509Certificate[] getAcceptedIssuers() {
					return origTrustmanager.getAcceptedIssuers();
				}

			} };
			sc.init(null, wrappedTrustManagers, null);
			SSLSocketFactory factory = sc.getSocketFactory();
			SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket,
					socket.getInetAddress().getHostAddress(), socket.getPort(),
					true);
			tagReader.setInputStream(sslSocket.getInputStream());
			Log.d(LOGTAG, "reset inputstream");
			tagWriter.setOutputStream(sslSocket.getOutputStream());
			Log.d(LOGTAG, "switch over seemed to work");
			isTlsEncrypted = true;
			sendStartStream();
			processStream(tagReader.readTag());
			sslSocket.close();
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (KeyManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void sendSaslAuth() throws IOException, XmlPullParserException {
		String saslString = SASL.plain(account.getUsername(),
				account.getPassword());
		Element auth = new Element("auth");
		auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "PLAIN");
		auth.setContent(saslString);
		Log.d(LOGTAG, account.getJid() + ": sending sasl " + auth.toString());
		tagWriter.writeElement(auth);
	}

	private void processStreamFeatures(Tag currentTag)
			throws XmlPullParserException, IOException {
		this.streamFeatures = tagReader.readElement(currentTag);
		Log.d(LOGTAG, account.getJid() + ": process stream features "
				+ streamFeatures);
		if (this.streamFeatures.hasChild("starttls")
				&& account.isOptionSet(Account.OPTION_USETLS)) {
			sendStartTLS();
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate) {
			sendSaslAuth();
		}
		if (this.streamFeatures.hasChild("bind") && shouldBind) {
			sendBindRequest();
			if (this.streamFeatures.hasChild("session")) {
				IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
				Element session = new Element("session");
				session.setAttribute("xmlns",
						"urn:ietf:params:xml:ns:xmpp-session");
				session.setContent("");
				startSession.addChild(session);
				sendIqPacket(startSession, null);
				tagWriter.writeElement(startSession);
			}
			Element presence = new Element("presence");

			tagWriter.writeElement(presence);
		}
	}

	private void sendBindRequest() throws IOException {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element bind = new Element("bind");
		bind.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-bind");
		iq.addChild(bind);
		this.sendIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				String resource = packet.findChild("bind").findChild("jid")
						.getContent().split("/")[1];
				account.setResource(resource);
				account.setStatus(Account.STATUS_ONLINE);
				if (statusListener != null) {
					statusListener.onStatusChanged(account);
				}
				sendServiceDiscovery();
			}
		});
	}

	private void sendServiceDiscovery() {
		IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setAttribute("to", account.getServer());
		Element query = new Element("query");
		query.setAttribute("xmlns", "http://jabber.org/protocol/disco#info");
		iq.addChild(query);
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.hasChild("query")) {
					List<Element> elements = packet.findChild("query")
							.getChildren();
					for (int i = 0; i < elements.size(); ++i) {
						if (elements.get(i).getName().equals("feature")) {
							discoFeatures.add(elements.get(i).getAttribute(
									"var"));
						}
					}
				}
				if (discoFeatures.contains("urn:xmpp:carbons:2")) {
					sendEnableCarbons();
				}
			}
		});
	}

	private void sendEnableCarbons() {
		Log.d(LOGTAG, account.getJid() + ": enable carbons");
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element enable = new Element("enable");
		enable.setAttribute("xmlns", "urn:xmpp:carbons:2");
		iq.addChild(enable);
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (!packet.hasChild("error")) {
					Log.d(LOGTAG, account.getJid()
							+ ": successfully enabled carbons");
				} else {
					Log.d(LOGTAG, account.getJid()
							+ ": error enableing carbons " + packet.toString());
				}
			}
		});
	}

	private void processStreamError(Tag currentTag) {
		Log.d(LOGTAG, "processStreamError");
	}

	private void sendStartStream() {
		Tag stream = Tag.start("stream:stream");
		stream.setAttribute("from", account.getJid());
		stream.setAttribute("to", account.getServer());
		stream.setAttribute("version", "1.0");
		stream.setAttribute("xml:lang", "en");
		stream.setAttribute("xmlns", "jabber:client");
		stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
		tagWriter.writeTag(stream);
	}

	private String nextRandomId() {
		return new BigInteger(50, random).toString(32);
	}

	public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
		String id = nextRandomId();
		packet.setAttribute("id", id);
		tagWriter.writeElement(packet);
		if (callback != null) {
			packetCallbacks.put(id, callback);
		}
	}

	public void sendMessagePacket(MessagePacket packet) {
		this.sendMessagePacket(packet, null);
	}

	public void sendMessagePacket(MessagePacket packet,
			OnMessagePacketReceived callback) {
		String id = nextRandomId();
		packet.setAttribute("id", id);
		tagWriter.writeElement(packet);
		if (callback != null) {
			packetCallbacks.put(id, callback);
		}
	}

	public void sendPresencePacket(PresencePacket packet) {
		this.sendPresencePacket(packet, null);
	}

	public PresencePacket sendPresencePacket(PresencePacket packet,
			OnPresencePacketReceived callback) {
		String id = nextRandomId();
		packet.setAttribute("id", id);
		tagWriter.writeElement(packet);
		if (callback != null) {
			packetCallbacks.put(id, callback);
		}
		return packet;
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

	public void setOnStatusChangedListener(OnStatusChanged listener) {
		this.statusListener = listener;
	}

	public void disconnect() {
		shouldConnect = false;
		tagWriter.writeTag(Tag.end("stream:stream"));
	}
}
