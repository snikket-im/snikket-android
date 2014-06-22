package eu.siacs.conversations.generator;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageGenerator {
	private MessagePacket preparePacket(Message message) {
		Conversation conversation = message.getConversation();
		Account account = conversation.getAccount();
		MessagePacket packet = new MessagePacket();
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			packet.setTo(message.getCounterpart());
			packet.setType(MessagePacket.TYPE_CHAT);
		} else {
			packet.setTo(message.getCounterpart().split("/")[0]);
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
		}
		packet.setFrom(account.getFullJid());
		packet.setId(message.getUuid());
		return packet;
	}
	
	public MessagePacket generateOtrChat(Message message) throws OtrException {
		Session otrSession = message.getConversation().getOtrSession();
		if (otrSession==null) {
			throw new OtrException(null);
		}
		MessagePacket packet = preparePacket(message);
		packet.addChild("private", "urn:xmpp:carbons:2");
		packet.addChild("no-copy", "urn:xmpp:hints");
		packet.setBody(otrSession.transformSending(message
				.getBody()));
		return packet;
	}
	
	public MessagePacket generateChat(Message message) {
		MessagePacket packet = preparePacket(message);
		packet.setBody(message.getBody());
		return packet;
	}
	
	public MessagePacket generatePgpChat(Message message) {
		MessagePacket packet = preparePacket(message);
		packet.setBody("This is an XEP-0027 encryted message");
		if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
			packet.addChild("x", "jabber:x:encrypted").setContent(
					message.getEncryptedBody());
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			packet.setBody(message.getBody());
		}
		return packet;
	}
}
