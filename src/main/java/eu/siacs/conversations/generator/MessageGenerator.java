package eu.siacs.conversations.generator;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageGenerator extends AbstractGenerator {
	public static final String OTR_FALLBACK_MESSAGE = "I would like to start a private (OTR encrypted) conversation but your client doesn’t seem to support that";
	private static final String OMEMO_FALLBACK_MESSAGE = "I sent you an OMEMO encrypted message but your client doesn’t seem to support that. Find more information on https://conversations.im/omemo";
	private static final String PGP_FALLBACK_MESSAGE = "I sent you a PGP encrypted message but your client doesn’t seem to support that.";

	public MessageGenerator(XmppConnectionService service) {
		super(service);
	}

	private MessagePacket preparePacket(Message message) {
		Conversation conversation = message.getConversation();
		Account account = conversation.getAccount();
		MessagePacket packet = new MessagePacket();
		if (conversation.getMode() == Conversation.MODE_SINGLE) {
			packet.setTo(message.getCounterpart());
			packet.setType(MessagePacket.TYPE_CHAT);
			packet.addChild("markable", "urn:xmpp:chat-markers:0");
			if (this.mXmppConnectionService.indicateReceived()) {
				packet.addChild("request", "urn:xmpp:receipts");
			}
		} else if (message.getType() == Message.TYPE_PRIVATE) {
			packet.setTo(message.getCounterpart());
			packet.setType(MessagePacket.TYPE_CHAT);
			if (this.mXmppConnectionService.indicateReceived()) {
				packet.addChild("request", "urn:xmpp:receipts");
			}
		} else {
			packet.setTo(message.getCounterpart().toBareJid());
			packet.setType(MessagePacket.TYPE_GROUPCHAT);
		}
		packet.setFrom(account.getJid());
		packet.setId(message.getUuid());
		if (message.edited()) {
			packet.addChild("replace","urn:xmpp:message-correct:0").setAttribute("id",message.getEditedId());
		}
		return packet;
	}

	public void addDelay(MessagePacket packet, long timestamp) {
		final SimpleDateFormat mDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
		mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		Element delay = packet.addChild("delay", "urn:xmpp:delay");
		Date date = new Date(timestamp);
		delay.setAttribute("stamp", mDateFormat.format(date));
	}

	public MessagePacket generateAxolotlChat(Message message, XmppAxolotlMessage axolotlMessage) {
		MessagePacket packet = preparePacket(message);
		if (axolotlMessage == null) {
			return null;
		}
		if (!recipientSupportsOmemo(message)) {
			packet.setBody(OMEMO_FALLBACK_MESSAGE);
		}
		packet.setAxolotlMessage(axolotlMessage.toElement());
		packet.addChild("store", "urn:xmpp:hints");
		return packet;
	}

	private static boolean recipientSupportsOmemo(Message message) {
		Contact c = message.getContact();
		return c != null && c.getPresences().allOrNonSupport(AxolotlService.PEP_DEVICE_LIST_NOTIFY);
	}

	public static void addMessageHints(MessagePacket packet) {
		packet.addChild("private", "urn:xmpp:carbons:2");
		packet.addChild("no-copy", "urn:xmpp:hints");
		packet.addChild("no-permanent-store", "urn:xmpp:hints");
		packet.addChild("no-permanent-storage", "urn:xmpp:hints"); //do not copy this. this is wrong. it is *store*
	}

	public MessagePacket generateOtrChat(Message message) {
		Session otrSession = message.getConversation().getOtrSession();
		if (otrSession == null) {
			return null;
		}
		MessagePacket packet = preparePacket(message);
		addMessageHints(packet);
		try {
			String content;
			if (message.hasFileOnRemoteHost()) {
				content = message.getFileParams().url.toString();
			} else {
				content = message.getBody();
			}
			packet.setBody(otrSession.transformSending(content)[0]);
			return packet;
		} catch (OtrException e) {
			return null;
		}
	}

	public MessagePacket generateChat(Message message) {
		MessagePacket packet = preparePacket(message);
		String content;
		if (message.hasFileOnRemoteHost()) {
			Message.FileParams fileParams = message.getFileParams();
			content = fileParams.url.toString();
			packet.addChild("x","jabber:x:oob").addChild("url").setContent(content);
		} else {
			content = message.getBody();
		}
		packet.setBody(content);
		return packet;
	}

	public MessagePacket generatePgpChat(Message message) {
		MessagePacket packet = preparePacket(message);
		packet.setBody(PGP_FALLBACK_MESSAGE);
		if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
			packet.addChild("x", "jabber:x:encrypted").setContent(message.getEncryptedBody());
		} else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			packet.addChild("x", "jabber:x:encrypted").setContent(message.getBody());
		}
		return packet;
	}

	public MessagePacket generateChatState(Conversation conversation) {
		final Account account = conversation.getAccount();
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_CHAT);
		packet.setTo(conversation.getJid().toBareJid());
		packet.setFrom(account.getJid());
		packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
		packet.addChild("no-store", "urn:xmpp:hints");
		packet.addChild("no-storage", "urn:xmpp:hints"); //wrong! don't copy this. Its *store*
		return packet;
	}

	public MessagePacket confirm(final Account account, final Jid to, final String id) {
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_CHAT);
		packet.setTo(to);
		packet.setFrom(account.getJid());
		Element received = packet.addChild("displayed","urn:xmpp:chat-markers:0");
		received.setAttribute("id", id);
		packet.addChild("store", "urn:xmpp:hints");
		return packet;
	}

	public MessagePacket conferenceSubject(Conversation conversation,String subject) {
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_GROUPCHAT);
		packet.setTo(conversation.getJid().toBareJid());
		Element subjectChild = new Element("subject");
		subjectChild.setContent(subject);
		packet.addChild(subjectChild);
		packet.setFrom(conversation.getAccount().getJid().toBareJid());
		return packet;
	}

	public MessagePacket directInvite(final Conversation conversation, final Jid contact) {
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_NORMAL);
		packet.setTo(contact);
		packet.setFrom(conversation.getAccount().getJid());
		Element x = packet.addChild("x", "jabber:x:conference");
		x.setAttribute("jid", conversation.getJid().toBareJid().toString());
		return packet;
	}

	public MessagePacket invite(Conversation conversation, Jid contact) {
		MessagePacket packet = new MessagePacket();
		packet.setTo(conversation.getJid().toBareJid());
		packet.setFrom(conversation.getAccount().getJid());
		Element x = new Element("x");
		x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
		Element invite = new Element("invite");
		invite.setAttribute("to", contact.toBareJid().toString());
		x.addChild(invite);
		packet.addChild(x);
		return packet;
	}

	public MessagePacket received(Account account, MessagePacket originalMessage, ArrayList<String> namespaces, int type) {
		MessagePacket receivedPacket = new MessagePacket();
		receivedPacket.setType(type);
		receivedPacket.setTo(originalMessage.getFrom());
		receivedPacket.setFrom(account.getJid());
		for(String namespace : namespaces) {
			receivedPacket.addChild("received", namespace).setAttribute("id", originalMessage.getId());
		}
		return receivedPacket;
	}

	public MessagePacket generateOtrError(Jid to, String id, String errorText) {
		MessagePacket packet = new MessagePacket();
		packet.setType(MessagePacket.TYPE_ERROR);
		packet.setAttribute("id",id);
		packet.setTo(to);
		Element error = packet.addChild("error");
		error.setAttribute("code","406");
		error.setAttribute("type","modify");
		error.addChild("not-acceptable","urn:ietf:params:xml:ns:xmpp-stanzas");
		error.addChild("text").setContent("?OTR Error:" + errorText);
		return packet;
	}
}
