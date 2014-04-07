package eu.siacs.conversations.services;

import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.JingleConnection;
import eu.siacs.conversations.xmpp.stanzas.jingle.JinglePacket;

public class JingleConnectionManager {
	
	private XmppConnectionService xmppConnectionService;
	
	private ConcurrentHashMap<String, JingleConnection> connections = new ConcurrentHashMap<String, JingleConnection>();
	
	public JingleConnectionManager(XmppConnectionService service) {
		this.xmppConnectionService = service;
	}
	
	public void deliverPacket(Account account, JinglePacket packet) {
		String id = generateInternalId(account.getJid(), packet.getFrom(), packet.getSessionId());
	}
	
	public JingleConnection createNewConnection(Message message) {
		Account account = message.getConversation().getAccount();
		JingleConnection connection = new JingleConnection(this,account, message.getCounterpart());
		String id = generateInternalId(account.getJid(), message.getCounterpart(), connection.getSessionId());
		connection.init(message);
		return connection;
	}
	
	private String generateInternalId(String account, String counterpart, String sid) {
		return account+"#"+counterpart+"#"+sid;
		
	}

	public XmppConnectionService getXmppConnectionService() {
		return this.xmppConnectionService;
	}

	public Element getPrimaryCanditate(String jid) {
		Element canditate = new Element("canditate");
		canditate.setAttribute("cid","122");
		canditate.setAttribute("port","1234");
		canditate.setAttribute("jid", jid);
		canditate.setAttribute("type", "assisted");
		return canditate;
	}
}
