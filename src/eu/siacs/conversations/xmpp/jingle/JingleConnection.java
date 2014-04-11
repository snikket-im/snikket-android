package eu.siacs.conversations.xmpp.jingle;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleConnection {

	private JingleConnectionManager mJingleConnectionManager;
	private XmppConnectionService mXmppConnectionService;
	
	public static final int STATUS_INITIATED = 0;
	public static final int STATUS_ACCEPTED = 1;
	public static final int STATUS_TERMINATED = 2;
	public static final int STATUS_FAILED = 99;
	
	private int status = -1;
	private Message message;
	private String sessionId;
	private Account account;
	private String initiator;
	private String responder;
	private List<Element> candidates = new ArrayList<Element>();
	private HashMap<String, SocksConnection> connections = new HashMap<String, SocksConnection>();
	
	private OnIqPacketReceived responseListener = new OnIqPacketReceived() {
		
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE_ERROR) {
				mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
				status = STATUS_FAILED;
			}
		}
	};
	
	public JingleConnection(JingleConnectionManager mJingleConnectionManager) {
		this.mJingleConnectionManager = mJingleConnectionManager;
		this.mXmppConnectionService = mJingleConnectionManager.getXmppConnectionService();
		this.sessionId = this.mJingleConnectionManager.nextRandomId();
	}
	
	public String getSessionId() {
		return this.sessionId;
	}
	
	public String getAccountJid() {
		return this.account.getJid();
	}
	
	public String getCounterPart() {
		return this.message.getCounterpart();
	}
	
	public void deliverPacket(JinglePacket packet) {
		
		if (packet.isAction("session-terminate")) {
			if (status == STATUS_INITIATED) {
				mXmppConnectionService.markMessage(message, Message.STATUS_SEND_REJECTED);
			}
			status = STATUS_TERMINATED;
		} else if (packet.isAction("session-accept")) {
			accept(packet);
		} else if (packet.isAction("transport-info")) {
			transportInfo(packet);
		} else {
			Log.d("xmppService","packet arrived in connection. action was "+packet.getAction());
		}
	}
	
	public void init(Message message) {
		this.message = message;
		this.account = message.getConversation().getAccount();
		this.initiator = this.account.getFullJid();
		this.responder = this.message.getCounterpart();
		if (this.candidates.size() > 0) {
			this.sendInitRequest();
		} else {
			this.mJingleConnectionManager.getPrimaryCandidate(account, new OnPrimaryCandidateFound() {
				
				@Override
				public void onPrimaryCandidateFound(boolean success, Element canditate) {
					if (success) {
						candidates.add(canditate);
					}
					sendInitRequest();
				}
			});
		}
		
	}
	
	private void sendInitRequest() {
		JinglePacket packet = this.bootstrapPacket();
		packet.setAction("session-initiate");
		packet.setInitiator(this.account.getFullJid());
		Content content = new Content();
		if (message.getType() == Message.TYPE_IMAGE) {
			content.setAttribute("creator", "initiator");
			content.setAttribute("name", "a-file-offer");
			content.offerFile(this.mXmppConnectionService.getFileBackend().getImageFile(message));
			content.setCandidates(this.mJingleConnectionManager.nextRandomId(),this.candidates);
			packet.setContent(content);
			Log.d("xmppService",packet.toString());
			account.getXmppConnection().sendIqPacket(packet, this.responseListener);
			this.status = STATUS_INITIATED;
		}
	}
	
	private JinglePacket bootstrapPacket() {
		JinglePacket packet = new JinglePacket();
		packet.setFrom(account.getFullJid());
		packet.setTo(this.message.getCounterpart()); //fixme, not right in all cases;
		packet.setSessionId(this.sessionId);
		return packet;
	}
	
	private void accept(JinglePacket packet) {
		Log.d("xmppService","session-accept: "+packet.toString());
		Content content = packet.getJingleContent();
		this.candidates.addAll(content.getCanditates());
		this.status = STATUS_ACCEPTED;
		this.connectWithCandidates();
		IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
		Log.d("xmppService","response "+response.toString());
		account.getXmppConnection().sendIqPacket(response, null);
	}
	
	private void transportInfo(JinglePacket packet) {
		Content content = packet.getJingleContent();
		Log.d("xmppService","transport info : "+content.toString());
		String cid = content.getUsedCandidate();
		if (cid!=null) {
			final File file = this.mXmppConnectionService.getFileBackend().getImageFile(this.message);
			final SocksConnection connection = this.connections.get(cid);
			if (connection.isProxy()) {
				IqPacket activation = new IqPacket(IqPacket.TYPE_SET);
				activation.setTo(connection.getJid());
				activation.query("http://jabber.org/protocol/bytestreams").setAttribute("sid", this.getSessionId());
				activation.query().addChild("activate").setContent(this.getResponder());
				Log.d("xmppService","connection is proxy. need to activate "+activation.toString());
				this.account.getXmppConnection().sendIqPacket(activation, new OnIqPacketReceived() {
					
					@Override
					public void onIqPacketReceived(Account account, IqPacket packet) {
						Log.d("xmppService","activation result: "+packet.toString());
						connection.send(file);
					}
				});
			} else {
				connection.send(file);
			}
		}
	}
	
	private void connectWithCandidates() {
		for(Element canditate : this.candidates) {
			String host = canditate.getAttribute("host");
			int port = Integer.parseInt(canditate.getAttribute("port"));
			String type = canditate.getAttribute("type");
			String jid = canditate.getAttribute("jid");
			SocksConnection socksConnection = new SocksConnection(this, host, jid, port,type);
			socksConnection.connect();
			this.connections.put(canditate.getAttribute("cid"), socksConnection);
		}
	}
	
	private void sendCandidateUsed(String cid) {
		
	}

	public String getInitiator() {
		return this.initiator;
	}
	
	public String getResponder() {
		return this.responder;
	}
}
