package eu.siacs.conversations.generator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageGenerator {
	private MessagePacket preparePacket(Message message, boolean addDelay) {
		Conversation conversation = message.getConversation();
		Account account = conversation.getAccount();
		MessagePacket packet = new MessagePacket();
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			packet.setTo(message.getCounterpart());
			packet.setType(MessagePacket.TYPE_CHAT);
			packet.addChild("markable", "urn:xmpp:chat-markers:0");
		} else {
			packet.setTo(message.getCounterpart().split("/")[0]);
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
		}
		packet.setFrom(account.getFullJid());
		packet.setId(message.getUuid());
		if (addDelay) {
			addDelay(packet,message.getTimeSent());
		}
		return packet;
	}
	
	private void addDelay(MessagePacket packet, long timestamp) {
		final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);
		mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		Element delay = packet.addChild("delay", "urn:xmpp:delay");
		Date date = new Date(timestamp);
		delay.setAttribute("stamp", mDateFormat.format(date));
	}
	
	public MessagePacket generateOtrChat(Message message) throws OtrException {
		return generateOtrChat(message, false);
	}
	
	public MessagePacket generateOtrChat(Message message, boolean addDelay) throws OtrException {
		Session otrSession = message.getConversation().getOtrSession();
		if (otrSession==null) {
			throw new OtrException(null);
		}
		MessagePacket packet = preparePacket(message,addDelay);
		packet.addChild("private", "urn:xmpp:carbons:2");
		packet.addChild("no-copy", "urn:xmpp:hints");
		packet.setBody(otrSession.transformSending(message
				.getBody()));
		return packet;
	}
	
	public MessagePacket generateChat(Message message) {
		return generateChat(message, false);
	}
	
	public MessagePacket generateChat(Message message, boolean addDelay) {
		MessagePacket packet = preparePacket(message,addDelay);
		packet.setBody(message.getBody());
		return packet;
	}
	
	public MessagePacket generatePgpChat(Message message) {
		return generatePgpChat(message, false);
	}
	
	public MessagePacket generatePgpChat(Message message, boolean addDelay) {
		MessagePacket packet = preparePacket(message,addDelay);
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
