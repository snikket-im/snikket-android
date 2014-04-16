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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.json.JSONException;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.Log;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.DNSHelper;
import eu.siacs.conversations.utils.zlib.ZLibOutputStream;
import eu.siacs.conversations.utils.zlib.ZLibInputStream;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.TagWriter;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.jingle.OnJinglePacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.AckPacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.EnablePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.RequestPacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.ResumePacket;

public class XmppConnection implements Runnable {

	protected Account account;
	private static final String LOGTAG = "xmppService";

	private WakeLock wakeLock;

	private SecureRandom random = new SecureRandom();

	private Socket socket;
	private XmlReader tagReader;
	private TagWriter tagWriter;

	private boolean shouldBind = true;
	private boolean shouldAuthenticate = true;
	private Element streamFeatures;
	private HashMap<String, List<String>> disco = new HashMap<String, List<String>>();
	
	private HashSet<String> pendingSubscriptions = new HashSet<String>();
	
	private String streamId = null;
	private int smVersion = 3;
	
	private int stanzasReceived = 0;
	private int stanzasSent = 0;
	
	public long lastPaketReceived = 0;
	public long lastPingSent = 0;
	public long lastConnect = 0;
	public long lastSessionStarted = 0;

	private static final int PACKET_IQ = 0;
	private static final int PACKET_MESSAGE = 1;
	private static final int PACKET_PRESENCE = 2;

	private Hashtable<String, PacketReceived> packetCallbacks = new Hashtable<String, PacketReceived>();
	private OnPresencePacketReceived presenceListener = null;
	private OnJinglePacketReceived jingleListener = null;
	private OnIqPacketReceived unregisteredIqListener = null;
	private OnMessagePacketReceived messageListener = null;
	private OnStatusChanged statusListener = null;
	private OnTLSExceptionReceived tlsListener = null;
	private OnBindListener bindListener = null;

	public XmppConnection(Account account, PowerManager pm) {
		this.account = account;
		this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,account.getJid());
		tagWriter = new TagWriter();
	}

	protected void changeStatus(int nextStatus) {
		if (account.getStatus() != nextStatus) {
			if ((nextStatus == Account.STATUS_OFFLINE)&&(account.getStatus() != Account.STATUS_CONNECTING)&&(account.getStatus() != Account.STATUS_ONLINE)) {
				return;
			}
			account.setStatus(nextStatus);
			if (statusListener != null) {
				statusListener.onStatusChanged(account);
			}
		}
	}

	protected void connect() {
		Log.d(LOGTAG,account.getJid()+ ": connecting");
		lastConnect = SystemClock.elapsedRealtime();
		try {
			shouldAuthenticate = shouldBind = !account.isOptionSet(Account.OPTION_REGISTER);
			tagReader = new XmlReader(wakeLock);
			tagWriter = new TagWriter();
			packetCallbacks.clear();
			this.changeStatus(Account.STATUS_CONNECTING);
			Bundle namePort = DNSHelper.getSRVRecord(account.getServer());
			if ("timeout".equals(namePort.getString("error"))) {
				Log.d(LOGTAG,account.getJid()+": dns timeout");
				this.changeStatus(Account.STATUS_OFFLINE);
				return;
			}
			String srvRecordServer = namePort.getString("name");
			String srvIpServer = namePort.getString("ipv4");
			int srvRecordPort = namePort.getInt("port");
			if (srvRecordServer != null) {
				if (srvIpServer != null) {
					Log.d(LOGTAG, account.getJid() + ": using values from dns "
						+ srvRecordServer + "[" + srvIpServer + "]:"
						+ srvRecordPort);
					socket = new Socket(srvIpServer, srvRecordPort);
				} else {
					Log.d(LOGTAG, account.getJid() + ": using values from dns "
						+ srvRecordServer + ":" + srvRecordPort);
					socket = new Socket(srvRecordServer, srvRecordPort);
				}
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
			if (account.getStatus() != Account.STATUS_TLS_ERROR) {
				this.changeStatus(Account.STATUS_OFFLINE);
			}
			if (wakeLock.isHeld()) {
				wakeLock.release();
			}
			return;
		} catch (NoSuchAlgorithmException e) {
			this.changeStatus(Account.STATUS_OFFLINE);
			Log.d(LOGTAG, "compression exception " + e.getMessage());
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
	}

	private void processStream(Tag currentTag) throws XmlPullParserException,
			IOException, NoSuchAlgorithmException {
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
			} else if (nextTag.isStart("compressed")) {
				switchOverToZLib(nextTag);
			} else if (nextTag.isStart("success")) {
				Log.d(LOGTAG, account.getJid()
						+ ": logged in");
				tagReader.readTag();
				tagReader.reset();
				sendStartStream();
				processStream(tagReader.readTag());
				break;
			} else if (nextTag.isStart("failure")) {
				Element failure = tagReader.readElement(nextTag);
				changeStatus(Account.STATUS_UNAUTHORIZED);
			} else if (nextTag.isStart("challenge")) {
				String challange = tagReader.readElement(nextTag).getContent();
				Element response = new Element("response");
				response.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-sasl");
				response.setContent(CryptoHelper.saslDigestMd5(account, challange));
				tagWriter.writeElement(response);
			} else if (nextTag.isStart("enabled")) {
				this.stanzasSent = 0;
				Element enabled = tagReader.readElement(nextTag);
				if ("true".equals(enabled.getAttribute("resume"))) {
					this.streamId = enabled.getAttribute("id");
					Log.d(LOGTAG,account.getJid()+": stream managment("+smVersion+") enabled (resumable)");
				} else {
					Log.d(LOGTAG,account.getJid()+": stream managment("+smVersion+") enabled");
				}
				this.lastSessionStarted = SystemClock.elapsedRealtime();
				this.stanzasReceived = 0;
				RequestPacket r = new RequestPacket(smVersion);
				tagWriter.writeStanzaAsync(r);
			} else if (nextTag.isStart("resumed")) {
				tagReader.readElement(nextTag);
				sendPing();
				changeStatus(Account.STATUS_ONLINE);
				Log.d(LOGTAG,account.getJid()+": session resumed");
			} else if (nextTag.isStart("r")) {
				tagReader.readElement(nextTag);
				AckPacket ack = new AckPacket(this.stanzasReceived,smVersion);
				//Log.d(LOGTAG,ack.toString());
				tagWriter.writeStanzaAsync(ack);
			} else if (nextTag.isStart("a")) {
				Element ack = tagReader.readElement(nextTag);
				lastPaketReceived = SystemClock.elapsedRealtime();
				int serverSequence = Integer.parseInt(ack.getAttribute("h"));
				if (serverSequence>this.stanzasSent) {
					this.stanzasSent = serverSequence;
				}
				//Log.d(LOGTAG,"server ack"+ack.toString()+" ("+this.stanzasSent+")");
			} else if (nextTag.isStart("failed")) {
				tagReader.readElement(nextTag);
				Log.d(LOGTAG,account.getJid()+": resumption failed");
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
				if ((packetType == PACKET_IQ)&&("jingle".equals(child.getName()))) {
					element = new JinglePacket();
					element.setAttributes(currentTag.getAttributes());
				}
				element.addChild(child);
			}
			nextTag = tagReader.readTag();
		}
		++stanzasReceived;
		lastPaketReceived = SystemClock.elapsedRealtime();
		return element;
	}

	private void processIq(Tag currentTag) throws XmlPullParserException,
			IOException {
		IqPacket packet = (IqPacket) processPacket(currentTag, PACKET_IQ);
		
		if (packet.getId() == null) {
			return; //an iq packet without id is definitely invalid
		}
		
		if (packet instanceof JinglePacket) {
			if (this.jingleListener !=null) {
				this.jingleListener.onJinglePacketReceived(account, (JinglePacket) packet);
			}
		} else {
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

	private void switchOverToZLib(Tag currentTag) throws XmlPullParserException,
			IOException, NoSuchAlgorithmException {
		tagReader.readTag(); // read tag close

		tagWriter.setOutputStream(new ZLibOutputStream(tagWriter.getOutputStream()));
		tagReader.setInputStream(new ZLibInputStream(tagReader.getInputStream()));

		sendStartStream();
		Log.d(LOGTAG,account.getJid()+": compression enabled");
		processStream(tagReader.readTag());
	}

	private void sendStartTLS() throws IOException {
		Tag startTLS = Tag.empty("starttls");
		startTLS.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-tls");
		tagWriter.writeTag(startTLS);
	}

	private void switchOverToTls(Tag currentTag) throws XmlPullParserException,
			IOException {
		Tag nextTag = tagReader.readTag(); // should be proceed end tag
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
						if (e.getCause() instanceof CertPathValidatorException) {
							String sha;
							try {
								MessageDigest sha1 = MessageDigest.getInstance("SHA1");
								sha1.update(chain[0].getEncoded());
								sha = CryptoHelper.bytesToHex(sha1.digest());
								if (!sha.equals(account.getSSLFingerprint())) {
									changeStatus(Account.STATUS_TLS_ERROR);
									if (tlsListener!=null) {
										tlsListener.onTLSExceptionReceived(sha,account);
									}
									throw new CertificateException();
								}
							} catch (NoSuchAlgorithmException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						} else {
							throw new CertificateException();
						}
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
			tagWriter.setOutputStream(sslSocket.getOutputStream());
			sendStartStream();
			Log.d(LOGTAG,account.getJid()+": TLS connection established");
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
		if (this.streamFeatures.hasChild("starttls")
				&& account.isOptionSet(Account.OPTION_USETLS)) {
			sendStartTLS();
		} else if (compressionAvailable()) {
			sendCompressionZlib();
		} else if (this.streamFeatures.hasChild("register")&&(account.isOptionSet(Account.OPTION_REGISTER))) {
				sendRegistryRequest();
		} else if (!this.streamFeatures.hasChild("register")&&(account.isOptionSet(Account.OPTION_REGISTER))) {
			changeStatus(Account.STATUS_REGISTRATION_NOT_SUPPORTED);
			disconnect(true);
		} else if (this.streamFeatures.hasChild("mechanisms")
				&& shouldAuthenticate) {
			List<String> mechanisms = extractMechanisms( streamFeatures.findChild("mechanisms"));
			if (mechanisms.contains("PLAIN")) {
				sendSaslAuthPlain();
			} else if (mechanisms.contains("DIGEST-MD5")) {
				sendSaslAuthDigestMd5();
			}
		} else if (this.streamFeatures.hasChild("sm","urn:xmpp:sm:"+smVersion) && streamId != null) {
			ResumePacket resume = new ResumePacket(this.streamId,stanzasReceived,smVersion);
			this.tagWriter.writeStanzaAsync(resume);
		} else if (this.streamFeatures.hasChild("bind") && shouldBind) {
			sendBindRequest();
			if (this.streamFeatures.hasChild("session")) {
				Log.d(LOGTAG,account.getJid()+": sending deprecated session");
				IqPacket startSession = new IqPacket(IqPacket.TYPE_SET);
				startSession.addChild("session","urn:ietf:params:xml:ns:xmpp-session"); //setContent("")
				this.sendIqPacket(startSession, null);
			}
		}
	}

	private boolean compressionAvailable() {
		if (!this.streamFeatures.hasChild("compression", "http://jabber.org/features/compress")) return false;
		if (!ZLibOutputStream.SUPPORTED) return false;
		if (!account.isOptionSet(Account.OPTION_USECOMPRESSION)) return false;

		Element compression = this.streamFeatures.findChild("compression", "http://jabber.org/features/compress");
		for (Element child : compression.getChildren()) {
			if (!"method".equals(child.getName())) continue;

			if ("zlib".equalsIgnoreCase(child.getContent())) {
				return true;
			}
		}
		return false;
	}

	private List<String> extractMechanisms(Element stream) {
		ArrayList<String> mechanisms = new ArrayList<String>(stream.getChildren().size());
		for(Element child : stream.getChildren()) {
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
				if (packet.query().hasChild("username")&&(packet.query().hasChild("password"))) {
					IqPacket register = new IqPacket(IqPacket.TYPE_SET);
					Element username = new Element("username").setContent(account.getUsername());
					Element password = new Element("password").setContent(account.getPassword());
					register.query("jabber:iq:register").addChild(username);
					register.query().addChild(password);
					sendIqPacket(register, new OnIqPacketReceived() {
						
						@Override
						public void onIqPacketReceived(Account account, IqPacket packet) {
							if (packet.getType()==IqPacket.TYPE_RESULT) {
								account.setOption(Account.OPTION_REGISTER, false);
								changeStatus(Account.STATUS_REGISTRATION_SUCCESSFULL);
							} else if (packet.hasChild("error")&&(packet.findChild("error").hasChild("conflict"))){
								changeStatus(Account.STATUS_REGISTRATION_CONFLICT);
							} else {
								changeStatus(Account.STATUS_REGISTRATION_FAILED);
								Log.d(LOGTAG,packet.toString());
							}
							disconnect(true);
						}
					});
				} else {
					changeStatus(Account.STATUS_REGISTRATION_FAILED);
					disconnect(true);
					Log.d(LOGTAG,account.getJid()+": could not register. instructions are"+instructions.getContent());
				}
			}
		});
	}

	private void sendInitialPresence() {
		PresencePacket packet = new PresencePacket();
		packet.setAttribute("from", account.getFullJid());
		if (account.getKeys().has("pgp_signature")) {
			try {
				String signature = account.getKeys().getString("pgp_signature");
				packet.addChild("status").setContent("online");
				packet.addChild("x","jabber:x:signed").setContent(signature);
			} catch (JSONException e) {
				//
			}
		}
		this.sendPresencePacket(packet);
	}

	private void sendBindRequest() throws IOException {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("bind", "urn:ietf:params:xml:ns:xmpp-bind").addChild("resource").setContent(account.getResource());
		this.sendIqPacket(iq, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				String resource = packet.findChild("bind").findChild("jid")
						.getContent().split("/")[1];
				account.setResource(resource);
				if (streamFeatures.hasChild("sm","urn:xmpp:sm:3")) {
					smVersion = 3;
					EnablePacket enable = new EnablePacket(smVersion);
					tagWriter.writeStanzaAsync(enable);
				} else if (streamFeatures.hasChild("sm","urn:xmpp:sm:2")) {
					smVersion = 2;
					EnablePacket enable = new EnablePacket(smVersion);
					tagWriter.writeStanzaAsync(enable);
				}
				sendInitialPresence();
				sendServiceDiscoveryInfo(account.getServer());
				sendServiceDiscoveryItems(account.getServer());
				if (bindListener !=null) {
					bindListener.onBind(account);
				}
				changeStatus(Account.STATUS_ONLINE);
			}
		});
	}

	private void sendServiceDiscoveryInfo(final String server) {
		IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setTo(server);
		iq.query("http://jabber.org/protocol/disco#info");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				List<Element> elements = packet.query().getChildren();
				List<String> features = new ArrayList<String>();
				for (int i = 0; i < elements.size(); ++i) {
					if (elements.get(i).getName().equals("feature")) {
						features.add(elements.get(i).getAttribute(
								"var"));
					}
				}
				disco.put(server, features);
				
				if (account.getServer().equals(server)) {
					enableAdvancedStreamFeatures();
				}
			}
		});
	}
	
	private void enableAdvancedStreamFeatures() {
		if (hasFeaturesCarbon()) {
			sendEnableCarbons();
		}
	}
	
	private void sendServiceDiscoveryItems(final String server) {
		IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
		iq.setTo(server);
		iq.query("http://jabber.org/protocol/disco#items");
		this.sendIqPacket(iq, new OnIqPacketReceived() {

			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				List<Element> elements = packet.query().getChildren();
				for (int i = 0; i < elements.size(); ++i) {
					if (elements.get(i).getName().equals("item")) {
						String jid = elements.get(i).getAttribute(
								"jid");
						sendServiceDiscoveryInfo(jid);
					}
				}
			}
		});
	}

	private void sendEnableCarbons() {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.addChild("enable","urn:xmpp:carbons:2");
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

	private void sendStartStream() throws IOException {
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
		if (packet.getId()==null) {
			String id = nextRandomId();
			packet.setAttribute("id", id);
		}
		packet.setFrom(account.getFullJid());
		this.sendPacket(packet, callback);
	}

	public void sendMessagePacket(MessagePacket packet) {
		this.sendPacket(packet, null);
	}

	public void sendMessagePacket(MessagePacket packet,
			OnMessagePacketReceived callback) {
		this.sendPacket(packet, callback);
	}

	public void sendPresencePacket(PresencePacket packet) {
		this.sendPacket(packet, null);
	}

	public void sendPresencePacket(PresencePacket packet,
			OnPresencePacketReceived callback) {
		this.sendPacket(packet, callback);
	}
	
	private synchronized void sendPacket(final AbstractStanza packet, PacketReceived callback) {
		// TODO dont increment stanza count if packet = request packet or ack;
		++stanzasSent;
		tagWriter.writeStanzaAsync(packet);
		if (callback != null) {
			if (packet.getId()==null) {
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
			iq.addChild("ping","urn:xmpp:ping");
			this.sendIqPacket(iq, null);
		}
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
	
	public void setOnJinglePacketReceivedListener(OnJinglePacketReceived listener) {
		this.jingleListener = listener;
	}

	public void setOnStatusChangedListener(OnStatusChanged listener) {
		this.statusListener = listener;
	}
	
	public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
		this.tlsListener = listener;
	}
	
	public void setOnBindListener(OnBindListener listener) {
		this.bindListener = listener;
	}

	public void disconnect(boolean force) {
		changeStatus(Account.STATUS_OFFLINE);
		Log.d(LOGTAG,"disconnecting");
		try {
		if (force) {
				socket.close();
				return;
		}
		if (tagWriter.isActive()) {
			tagWriter.finish();
			while(!tagWriter.finished()) {
				//Log.d(LOGTAG,"not yet finished");
				Thread.sleep(100);
			}
			tagWriter.writeTag(Tag.end("stream:stream"));
		}
		} catch (IOException e) {
			Log.d(LOGTAG,"io exception during disconnect");
		} catch (InterruptedException e) {
			Log.d(LOGTAG,"interupted while waiting for disconnect");
		}
	}
	
	public boolean hasFeatureRosterManagment() {
		if (this.streamFeatures==null) {
			return false;
		} else {
			return this.streamFeatures.hasChild("ver");
		}
	}
	
	public boolean hasFeatureStreamManagment() {
		if (this.streamFeatures==null) {
			return false;
		} else {
			return this.streamFeatures.hasChild("sm");
		}
	}
	
	public boolean hasFeaturesCarbon() {
		return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
	}
	
	public boolean hasDiscoFeature(String server, String feature) {
		if (!disco.containsKey(server)) {
			return false;
		}
		return disco.get(server).contains(feature);
	}
	
	public String findDiscoItemByFeature(String feature) {
		Iterator<Entry<String, List<String>>> it = this.disco.entrySet().iterator();
	    while (it.hasNext()) {
	    	Entry<String, List<String>> pairs = it.next();
	        if (pairs.getValue().contains(feature)) {
	        	return pairs.getKey();
	        }
	        it.remove();
	    }
		return null;
	}

	public void r() {
		this.tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
	}

	public int getReceivedStanzas() {
		return this.stanzasReceived;
	}
	
	public int getSentStanzas() {
		return this.stanzasSent;
	}

	public String getMucServer() {
		return findDiscoItemByFeature("http://jabber.org/protocol/muc");
	}
	
	public boolean hasPendingSubscription(String jid) {
		return this.pendingSubscriptions.contains(jid);
	}
	
	public void addPendingSubscription(String jid) {
		Log.d(LOGTAG,"adding "+jid+" to pending subscriptions");
		this.pendingSubscriptions.add(jid);
	}
}
