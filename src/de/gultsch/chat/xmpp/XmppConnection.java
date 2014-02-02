package de.gultsch.chat.xmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Hashtable;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.xmlpull.v1.XmlPullParserException;

import android.os.PowerManager;
import android.util.Log;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.utils.SASL;
import de.gultsch.chat.xml.Element;
import de.gultsch.chat.xml.Tag;
import de.gultsch.chat.xml.XmlReader;
import de.gultsch.chat.xml.TagWriter;

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
	private boolean shouldUseTLS = false;
	private boolean shouldReConnect = true;
	private boolean shouldBind = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	
	private static final int PACKET_IQ = 0;
	private static final int PACKET_MESSAGE = 1;
	private static final int PACKET_PRESENCE = 2;
	
	private Hashtable<String, OnIqPacketReceived> iqPacketCallbacks = new Hashtable<String, OnIqPacketReceived>();
	private OnPresencePacketReceived presenceListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;
	private OnMessagePacketReceived messageListener = null;
	
	private String resource = null;

	public XmppConnection(Account account, PowerManager pm) {
		this.account = account;
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"XmppConnection");
		tagReader = new XmlReader(wakeLock);
		tagWriter = new TagWriter();
	}

	protected void connect() {
		try {
			socket = new Socket(account.getServer(), 5222);
			Log.d(LOGTAG, "starting new socket");
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
				} else {
					Log.d(LOGTAG, "found unexpected tag: " + nextTag.getName());
					return;
				}
			}
		} catch (UnknownHostException e) {
			Log.d(LOGTAG, "error during connect. unknown host");
			return;
		} catch (IOException e) {
			Log.d(LOGTAG, "error during connect. io exception. falscher port?");
			return;
		} catch (XmlPullParserException e) {
			Log.d(LOGTAG,"xml exception "+e.getMessage());
			return;
		}
	}

	@Override
	public void run() {
		while(shouldReConnect) {
			connect();
			try {
				Thread.sleep(30000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void processStream(Tag currentTag) throws XmlPullParserException,
			IOException {
		Log.d(LOGTAG, "process Stream");
		Tag nextTag;
		while (!(nextTag = tagReader.readTag()).isEnd("stream")) {
			if (nextTag.isStart("error")) {
				processStreamError(nextTag);
			} else if (nextTag.isStart("features")) {
				processStreamFeatures(nextTag);
			} else if (nextTag.isStart("proceed")) {
				switchOverToTls(nextTag);
			} else if (nextTag.isStart("success")) {
				isAuthenticated = true;
				Log.d(LOGTAG,"read success tag in stream. reset again");
				tagReader.readTag();
				tagReader.reset();
				sendStartStream();
				processStream(tagReader.readTag());
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
		}
	}
	
	private Element processPacket(Tag currentTag, int packetType) throws XmlPullParserException, IOException {
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
		while(!nextTag.isEnd(element.getName())) {
			if (!nextTag.isNo()) {
				Element child = tagReader.readElement(nextTag);
				element.addChild(child);
			}
			nextTag = tagReader.readTag();
		}
		return element;
	}
	

	private IqPacket processIq(Tag currentTag) throws XmlPullParserException, IOException {
		IqPacket packet = (IqPacket) processPacket(currentTag,PACKET_IQ);
		if (iqPacketCallbacks.containsKey(packet.getId())) {
			iqPacketCallbacks.get(packet.getId()).onIqPacketReceived(account,packet);
			iqPacketCallbacks.remove(packet.getId());
		} else if (this.unregisteredIqListener != null) {
			this.unregisteredIqListener.onIqPacketReceived(account,packet);
		}
		return packet;
	}
	
	private void processMessage(Tag currentTag) throws XmlPullParserException, IOException {
		MessagePacket packet = (MessagePacket) processPacket(currentTag, PACKET_MESSAGE);
		if (this.messageListener != null) {
			this.messageListener.onMessagePacketReceived(account,packet);
		}
	}
	
	private void processPresence(Tag currentTag) throws XmlPullParserException, IOException {
		PresencePacket packet = (PresencePacket) processPacket(currentTag, PACKET_PRESENCE);
		if (this.presenceListener != null) {
			this.presenceListener.onPresencePacketReceived(account,packet);
		}
	}

	private void sendStartTLS() throws XmlPullParserException, IOException {
		Tag startTLS = Tag.empty("starttls");
		startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
		Log.d(LOGTAG, "sending starttls");
		tagWriter.writeTag(startTLS).flush();
	}

	private void switchOverToTls(Tag currentTag) throws XmlPullParserException,
			IOException {
		Tag nextTag = tagReader.readTag(); // should be proceed end tag
		Log.d(LOGTAG, "now switch to ssl");
		SSLSocket sslSocket;
		try {
			sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory
					.getDefault()).createSocket(socket, socket.getInetAddress()
					.getHostAddress(), socket.getPort(), true);
			tagReader.setInputStream(sslSocket.getInputStream());
			Log.d(LOGTAG, "reset inputstream");
			tagWriter.setOutputStream(sslSocket.getOutputStream());
			Log.d(LOGTAG, "switch over seemed to work");
			isTlsEncrypted = true;
			sendStartStream();
			processStream(tagReader.readTag());
		} catch (IOException e) {
			Log.d(LOGTAG, "error on ssl" + e.getMessage());
		}
	}

	private void sendSaslAuth() throws IOException, XmlPullParserException {
		String saslString = SASL.plain(account.getUsername(),
				account.getPassword());
		Element auth = new Element("auth");
		auth.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
		auth.setAttribute("mechanism", "PLAIN");
		auth.setContent(saslString);
		Log.d(LOGTAG,"sending sasl "+auth.toString());
		tagWriter.writeElement(auth);
		tagWriter.flush();
	}

	private void processStreamFeatures(Tag currentTag)
			throws XmlPullParserException, IOException {
		this.streamFeatures = tagReader.readElement(currentTag);
		Log.d(LOGTAG,"process stream features "+streamFeatures);
		if (this.streamFeatures.hasChild("starttls")&&shouldUseTLS) {
			sendStartTLS();
		}
		if (this.streamFeatures.hasChild("mechanisms")&&shouldAuthenticate) {
			sendSaslAuth();
		}
		if (this.streamFeatures.hasChild("bind")&&shouldBind) {
			sendBindRequest();
			if (this.streamFeatures.hasChild("session")) {
				IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
				Element session = new Element("session");
				session.setAttribute("xmlns","urn:ietf:params:xml:ns:xmpp-session");
				session.setContent("");
				startSession.addChild(session);
				sendIqPacket(startSession, null);
				tagWriter.writeElement(startSession);
				tagWriter.flush();
			}
			Element presence = new Element("presence");
			
			tagWriter.writeElement(presence);
			tagWriter.flush();
		}
	}

	private void sendBindRequest() throws IOException {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		Element bind = new Element("bind");
		bind.setAttribute("xmlns","urn:ietf:params:xml:ns:xmpp-bind");
		iq.addChild(bind);
		this.sendIqPacket(iq, new OnIqPacketReceived() {	
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				resource = packet.findChild("bind").findChild("jid").getContent().split("/")[1];
				Log.d(LOGTAG,"answer for our bind was: "+packet.toString());
				Log.d(LOGTAG,"new resource is "+resource);
			}
		});
	}

	private void processStreamError(Tag currentTag) {
		Log.d(LOGTAG, "processStreamError");
	}

	private void sendStartStream() throws IOException {
		Tag stream = Tag.start("stream");
		stream.setAttribute("from", account.getJid());
		stream.setAttribute("to", account.getServer());
		stream.setAttribute("version", "1.0");
		stream.setAttribute("xml:lang", "en");
		stream.setAttribute("xmlns", "jabber:client");
		stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
		tagWriter.writeTag(stream).flush();
	}

	private String nextRandomId() {
		return new BigInteger(50, random).toString(32);
	}
	
	public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) throws IOException {
		String id = nextRandomId();
		packet.setAttribute("id",id);
		tagWriter.writeElement(packet);
		if (callback != null) {
			iqPacketCallbacks.put(id, callback);
		}
		tagWriter.flush();
		Log.d(LOGTAG,"sending: "+packet.toString());
	}
	
	public void sendMessagePacket(MessagePacket packet) throws IOException {
		tagWriter.writeElement(packet);
		tagWriter.flush();
	}
	
	public void sendPresencePacket(PresencePacket packet) throws IOException {
		tagWriter.writeElement(packet);
		tagWriter.flush();
	}
	
	public void setOnMessagePacketReceivedListener(OnMessagePacketReceived listener) {
		this.messageListener = listener;
	}
	
	public void setOnUnregisteredIqPacketReceivedListener(OnIqPacketReceived listener) {
		this.unregisteredIqListener = listener;
	}
	
	public void setOnPresencePacketReceivedListener(OnPresencePacketReceived listener) {
		this.presenceListener = listener;
	}
}
