package eu.siacs.conversations.xmpp;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.JingleConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.jingle.Content;
import eu.siacs.conversations.xmpp.stanzas.jingle.JinglePacket;

public class JingleConnection {

	private JingleConnectionManager mJingleConnectionManager;
	private XmppConnectionService mXmppConnectionService;
	
	private String sessionId;
	private Account account;
	private String counterpart;
	private List<Element> canditates = new ArrayList<Element>();
	
	public JingleConnection(JingleConnectionManager mJingleConnectionManager, Account account, String counterpart) {
		this.mJingleConnectionManager = mJingleConnectionManager;
		this.mXmppConnectionService = mJingleConnectionManager.getXmppConnectionService();
		this.account = account;
		this.counterpart = counterpart;
		SecureRandom random = new SecureRandom();
		sessionId = new BigInteger(100, random).toString(32);
		this.canditates.add(this.mJingleConnectionManager.getPrimaryCanditate(account.getJid()));
	}
	
	public String getSessionId() {
		return this.sessionId;
	}
	
	public void init(Message message) {
		JinglePacket packet = this.bootstrapPacket();
		packet.setAction("session-initiate");
		packet.setInitiator(this.account.getFullJid());
		Content content = new Content();
		if (message.getType() == Message.TYPE_IMAGE) {
			//creator='initiator' name='a-file-offer'
			content.setAttribute("creator", "initiator");
			content.setAttribute("name", "a-file-offer");
			content.offerFile(this.mXmppConnectionService.getFileBackend().getImageFile(message));
			content.setCanditates(this.canditates);
			packet.setContent(content);
			Log.d("xmppService",packet.toString());
			account.getXmppConnection().sendIqPacket(packet, null);
		}
	}
	
	private JinglePacket bootstrapPacket() {
		JinglePacket packet = new JinglePacket();
		packet.setFrom(account.getFullJid());
		packet.setTo(this.counterpart+"/Gajim");
		packet.setSessionId(this.sessionId);
		return packet;
	}

}
