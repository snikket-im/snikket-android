package de.gultsch.chat.xmpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;

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
		} catch (UnknownHostException e) {
			Log.d(LOGTAG, "error during connect. unknown host");
		} catch (IOException e) {
			Log.d(LOGTAG, "error during connect. io exception. falscher port?");
		}
	}

	@Override
	public void run() {
		connect();
		try {
			tagWriter.beginDocument();
			sendStartStream();
			Tag nextTag;
			while ((nextTag = tagReader.readTag()) != null) {
				if (nextTag.isStart("stream")) {
					processStream(nextTag);
				} else {
					Log.d(LOGTAG, "found unexpected tag: " + nextTag.getName());
				}
			}
		} catch (XmlPullParserException e) {
			Log.d(LOGTAG,
					"xml error during normal read. maybe missformed xml? "
							+ e.getMessage());
		} catch (IOException e) {
			Log.d(LOGTAG, "io exception during read. connection lost?");
		}
	}

	private void processStream(Tag currentTag) throws XmlPullParserException,
			IOException {
		Log.d(LOGTAG, "process Stream");
		Tag nextTag;
		while ((nextTag = tagReader.readTag()) != null) {
			if (nextTag.isStart("error")) {
				processStreamError(nextTag);
			} else if (nextTag.isStart("features")) {
				processStreamFeatures(nextTag);
				if (!isTlsEncrypted) {
					sendStartTLS();
				}
				if ((!isAuthenticated) && (isTlsEncrypted)) {
					sendSaslAuth();
				}
				if ((isAuthenticated)&&(isTlsEncrypted)) {
					sendBindRequest();
				}
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
			} else if (nextTag.isEnd("stream")) {
				break;
			} else {
				Log.d(LOGTAG, "found unexpected tag: " + nextTag.getName()
						+ " as child of " + currentTag.getName());
			}
		}
	}

	private void processIq(Tag currentTag) throws XmlPullParserException, IOException {
		int typ = -1;
		if (currentTag.getAttribute("type").equals("result")) {
			typ = IqPacket.TYPE_RESULT;
		}
		IqPacket iq = new IqPacket(currentTag.getAttribute("id"),typ);
		Tag nextTag = tagReader.readTag();
		while(!nextTag.isEnd("iq")) {
			Element element = tagReader.readElement(nextTag);
			iq.addChild(element);
			nextTag = tagReader.readTag();
		}
		Log.d(LOGTAG,"this is what i understood: "+iq.toString());
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
		Log.d(LOGTAG, "processStreamFeatures");
		
		Element streamFeatures = new Element("features");
		
		Tag nextTag = tagReader.readTag();
		while(!nextTag.isEnd("features")) {
			Element element = tagReader.readElement(nextTag);
			streamFeatures.addChild(element);
			nextTag = tagReader.readTag();
		}	
	}

	private void sendBindRequest() throws IOException {
		IqPacket iq = new IqPacket(nextRandomId(),IqPacket.TYPE_SET);
		Element bind = new Element("bind");
		bind.setAttribute("xmlns","urn:ietf:params:xml:ns:xmpp-bind");
		iq.addChild(bind);
		Log.d(LOGTAG,"sending bind request: "+iq.toString());
		tagWriter.writeElement(iq);
		tagWriter.flush();
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
}
