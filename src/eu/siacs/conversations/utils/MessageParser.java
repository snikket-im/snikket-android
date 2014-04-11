package eu.siacs.conversations.utils;

import java.util.List;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import android.util.Log;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser {
	
	protected static final String LOGTAG = "xmppService";
	
	public static Message parsePlainTextChat(MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_NONE, Message.STATUS_RECIEVED);
	}
	
	public static Message parsePgpChat(String pgpBody, MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		return new Message(conversation, packet.getFrom(), pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECIEVED);
	}
	
	public static Message parseOtrChat(MessagePacket packet, Account account, XmppConnectionService service) {
		boolean properlyAddressed = (packet.getTo().split("/").length == 2) || (account.countPresences() == 1);
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],false);
		String body = packet.getBody();
		if (!conversation.hasValidOtrSession()) {
			if (properlyAddressed) {
				Log.d("xmppService","starting new otr session with "+packet.getFrom()+" because no valid otr session has been found");
				conversation.startOtrSession(service.getApplicationContext(), fromParts[1],false);
			} else {
				Log.d("xmppService",account.getJid()+": ignoring otr session with "+fromParts[0]);
				return null;
			}
		} else {
			String foreignPresence = conversation.getOtrSession().getSessionID().getUserID();
			if (!foreignPresence.equals(fromParts[1])) {
				conversation.resetOtrSession();
				if (properlyAddressed) {
					Log.d("xmppService","replacing otr session with "+packet.getFrom());
					conversation.startOtrSession(service.getApplicationContext(), fromParts[1],false);
				} else {
					return null;
				}
			}
		}
		try {
			Session otrSession = conversation.getOtrSession();
			SessionStatus before = otrSession
					.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			if ((before != after)
					&& (after == SessionStatus.ENCRYPTED)) {
				Log.d(LOGTAG, "otr session etablished");
				List<Message> messages = conversation
						.getMessages();
				for (int i = 0; i < messages.size(); ++i) {
					Message msg = messages.get(i);
					if ((msg.getStatus() == Message.STATUS_UNSEND)
							&& (msg.getEncryption() == Message.ENCRYPTION_OTR)) {
						MessagePacket outPacket = service.prepareMessagePacket(
								account, msg, otrSession);
						msg.setStatus(Message.STATUS_SEND);
						service.databaseBackend.updateMessage(msg);
						account.getXmppConnection()
								.sendMessagePacket(outPacket);
					}
				}
				if (service.convChangedListener!=null) {
					service.convChangedListener.onConversationListChanged();
				}
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				Log.d(LOGTAG,"otr session stoped");
			}
			//isEmpty is a work around for some weird clients which send emtpty strings over otr
			if ((body == null)||(body.isEmpty())) {
				return null;
			}
			return new Message(conversation, packet.getFrom(), body, Message.ENCRYPTION_OTR,Message.STATUS_RECIEVED);
		} catch (Exception e) {
			conversation.resetOtrSession();
			return null;
		}
	}
	
	public static Message parseGroupchat(MessagePacket packet, Account account, XmppConnectionService service) {
		int status;
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = service.findOrCreateConversation(account, fromParts[0],true);
		if (packet.hasChild("subject")) {
			conversation.getMucOptions().setSubject(packet.findChild("subject").getContent());
			service.updateConversationInGui();
			return null;
		}
		if ((fromParts.length == 1)) {
			return null;
		}
		String counterPart = fromParts[1];
		if (counterPart.equals(conversation.getMucOptions().getNick())) {
			status = Message.STATUS_SEND;
		} else {
			status = Message.STATUS_RECIEVED;
		}
		return new Message(conversation, counterPart, packet.getBody(), Message.ENCRYPTION_NONE, status);
	}

	public static Message parseCarbonMessage(MessagePacket packet,
			Account account, XmppConnectionService service) {
		// TODO Auto-generated method stub
		int status;
		String fullJid;
		Element forwarded;
		if (packet.hasChild("received")) {
			forwarded = packet.findChild("received").findChild(
					"forwarded");
			status = Message.STATUS_RECIEVED;
		} else if (packet.hasChild("sent")) {
			forwarded = packet.findChild("sent").findChild(
					"forwarded");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}
		if (forwarded==null) {
			return null;
		}
		Element message = forwarded.findChild("message");
		if ((message == null) || (!message.hasChild("body")))
			return null; // either malformed or boring
		if (status == Message.STATUS_RECIEVED) {
			fullJid = message.getAttribute("from");
		} else {
			fullJid = message.getAttribute("to");
		}
		String[] parts = fullJid.split("/");
		Conversation conversation = service.findOrCreateConversation(account, parts[0],false);
		return new Message(conversation,fullJid, message.findChild("body").getContent(), Message.ENCRYPTION_NONE,status);
	}

	public static void parseError(MessagePacket packet, Account account, XmppConnectionService service) {
		String[] fromParts = packet.getFrom().split("/");
		service.markMessage(account, fromParts[0], packet.getId(), Message.STATUS_SEND_FAILED);
	}

	public static String getPgpBody(MessagePacket packet) {
		for(Element child : packet.getChildren()) {
			if (child.getName().equals("x")&&child.getAttribute("xmlns").equals("jabber:x:encrypted")) {
				return child.getContent();
			}
		}
		return null;
	}
}
