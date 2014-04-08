package eu.siacs.conversations.xmpp.jingle;

import java.util.ArrayList;
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
	
	private Message message;
	private String sessionId;
	private Account account;
	private String initiator;
	private String responder;
	private List<Element> canditates = new ArrayList<Element>();
	
	private OnIqPacketReceived responseListener = new OnIqPacketReceived() {
		
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			Log.d("xmppService",packet.toString());
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
	
	public void init(Message message) {
		this.message = message;
		this.account = message.getConversation().getAccount();
		this.initiator = this.account.getFullJid();
		if (this.canditates.size() > 0) {
			this.sendInitRequest();
		} else {
			this.mJingleConnectionManager.getPrimaryCanditate(account, new OnPrimaryCanditateFound() {
				
				@Override
				public void onPrimaryCanditateFound(boolean success, Element canditate) {
					if (success) {
						canditates.add(canditate);
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
			content.setCanditates(this.canditates);
			packet.setContent(content);
			Log.d("xmppService",packet.toString());
			account.getXmppConnection().sendIqPacket(packet, this.responseListener);
		}
	}
	
	private JinglePacket bootstrapPacket() {
		JinglePacket packet = new JinglePacket();
		packet.setFrom(account.getFullJid());
		packet.setTo(this.message.getCounterpart()+"/Gajim"); //fixme, not right in all cases;
		packet.setSessionId(this.sessionId);
		return packet;
	}

}
