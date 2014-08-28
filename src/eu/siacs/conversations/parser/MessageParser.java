package eu.siacs.conversations.parser;

import android.os.SystemClock;
import android.util.Log;
import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser extends AbstractParser implements
		OnMessagePacketReceived {

	private long lastCarbonMessageReceived = -XmppConnectionService.CARBON_GRACE_PERIOD;

	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private Message parseChat(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fromParts[0], false);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		updateLastseen(packet, account, true);
		String pgpBody = getPgpBody(packet);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, packet.getFrom(),
					pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
		} else {
			finishedMessage = new Message(conversation, packet.getFrom(),
					packet.getBody(), Message.ENCRYPTION_NONE,
					Message.STATUS_RECEIVED);
		}
		finishedMessage.setRemoteMsgId(packet.getId());
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& fromParts.length >= 2) {
			finishedMessage.setType(Message.TYPE_PRIVATE);
			finishedMessage.setPresence(fromParts[1]);
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(fromParts[1]));
			if (conversation.hasDuplicateMessage(finishedMessage)) {
				return null;
			}

		}
		finishedMessage.setTime(getTimestamp(packet));
		return finishedMessage;
	}

	private Message parseOtrChat(MessagePacket packet, Account account) {
		boolean properlyAddressed = (packet.getTo().split("/").length == 2)
				|| (account.countPresences() == 1);
		String[] fromParts = packet.getFrom().split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fromParts[0], false);
		String presence;
		if (fromParts.length >= 2) {
			presence = fromParts[1];
		} else {
			presence = "";
		}
		updateLastseen(packet, account, true);
		String body = packet.getBody();
		if (!conversation.hasValidOtrSession()) {
			if (properlyAddressed) {
				conversation.startOtrSession(
						mXmppConnectionService.getApplicationContext(),
						presence, false);
			} else {
				return null;
			}
		} else {
			String foreignPresence = conversation.getOtrSession()
					.getSessionID().getUserID();
			if (!foreignPresence.equals(presence)) {
				conversation.endOtrIfNeeded();
				if (properlyAddressed) {
					conversation.startOtrSession(
							mXmppConnectionService.getApplicationContext(),
							presence, false);
				} else {
					return null;
				}
			}
		}
		try {
			Session otrSession = conversation.getOtrSession();
			SessionStatus before = otrSession.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			if ((before != after) && (after == SessionStatus.ENCRYPTED)) {
				mXmppConnectionService.onOtrSessionEstablished(conversation);
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.resetOtrSession();
				mXmppConnectionService.updateConversationUi();
			}
			if ((body == null) || (body.isEmpty())) {
				return null;
			}
			if (body.startsWith(CryptoHelper.FILETRANSFER)) {
				String key = body.substring(CryptoHelper.FILETRANSFER.length());
				conversation.setSymmetricKey(CryptoHelper.hexToBytes(key));
				return null;
			}
			conversation
					.setLatestMarkableMessageId(getMarkableMessageId(packet));
			Message finishedMessage = new Message(conversation,
					packet.getFrom(), body, Message.ENCRYPTION_OTR,
					Message.STATUS_RECEIVED);
			finishedMessage.setTime(getTimestamp(packet));
			finishedMessage.setRemoteMsgId(packet.getId());
			return finishedMessage;
		} catch (Exception e) {
			String receivedId = packet.getId();
			if (receivedId != null) {
				mXmppConnectionService.replyWithNotAcceptable(account, packet);
			}
			conversation.endOtrIfNeeded();
			return null;
		}
	}

	private Message parseGroupchat(MessagePacket packet, Account account) {
		int status;
		String[] fromParts = packet.getFrom().split("/");
		if (mXmppConnectionService.find(account.pendingConferenceLeaves,
				account, fromParts[0]) != null) {
			return null;
		}
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fromParts[0], true);
		if (packet.hasChild("subject")) {
			conversation.getMucOptions().setSubject(
					packet.findChild("subject").getContent());
			mXmppConnectionService.updateConversationUi();
			return null;
		}
		if ((fromParts.length == 1)) {
			return null;
		}
		String counterPart = fromParts[1];
		if (counterPart.equals(conversation.getMucOptions().getActualNick())) {
			if (mXmppConnectionService.markMessage(conversation,
					packet.getId(), Message.STATUS_SEND)) {
				return null;
			} else {
				status = Message.STATUS_SEND;
			}
		} else {
			status = Message.STATUS_RECEIVED;
		}
		String pgpBody = getPgpBody(packet);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		Message finishedMessage;
		if (pgpBody == null) {
			finishedMessage = new Message(conversation, counterPart,
					packet.getBody(), Message.ENCRYPTION_NONE, status);
		} else {
			finishedMessage = new Message(conversation, counterPart, pgpBody,
					Message.ENCRYPTION_PGP, status);
		}
		finishedMessage.setRemoteMsgId(packet.getId());
		if (status == Message.STATUS_RECEIVED) {
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(counterPart));
		}
		if (packet.hasChild("delay") && conversation.hasDuplicateMessage(finishedMessage)) {
			return null;
		}
		finishedMessage.setTime(getTimestamp(packet));
		return finishedMessage;
	}

	private Message parseCarbonMessage(MessagePacket packet, Account account) {
		int status;
		String fullJid;
		Element forwarded;
		if (packet.hasChild("received")) {
			forwarded = packet.findChild("received").findChild("forwarded");
			status = Message.STATUS_RECEIVED;
		} else if (packet.hasChild("sent")) {
			forwarded = packet.findChild("sent").findChild("forwarded");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}
		if (forwarded == null) {
			return null;
		}
		Element message = forwarded.findChild("message");
		if ((message == null) || (!message.hasChild("body"))) {
			if (status == Message.STATUS_RECEIVED && message.getAttribute("from")!=null) {
				parseNormal(message, account);
			}
			return null;
		}
		if (status == Message.STATUS_RECEIVED) {
			fullJid = message.getAttribute("from");
			if (fullJid == null) {
				return null;
			} else {
				updateLastseen(message, account, true);
			}
		} else {
			fullJid = message.getAttribute("to");
			if (fullJid == null) {
				return null;
			}
		}
		String[] parts = fullJid.split("/");
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, parts[0], false);
		conversation.setLatestMarkableMessageId(getMarkableMessageId(packet));
		
		String pgpBody = getPgpBody(message);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, fullJid, pgpBody,
					Message.ENCRYPTION_PGP, status);
		} else {
			String body = message.findChild("body").getContent();
			finishedMessage = new Message(conversation, fullJid, body,
					Message.ENCRYPTION_NONE, status);
		}
		finishedMessage.setTime(getTimestamp(message));
		finishedMessage.setRemoteMsgId(message.getAttribute("id"));
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& parts.length >= 2) {
			finishedMessage.setType(Message.TYPE_PRIVATE);
			finishedMessage.setPresence(parts[1]);
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(parts[1]));
			if (conversation.hasDuplicateMessage(finishedMessage)) {
				return null;
			}
		}
		
		return finishedMessage;
	}

	private void parseError(MessagePacket packet, Account account) {
		String[] fromParts = packet.getFrom().split("/");
		mXmppConnectionService.markMessage(account, fromParts[0],
				packet.getId(), Message.STATUS_SEND_FAILED);
	}

	private void parseNormal(Element packet, Account account) {
		if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
			Element event = packet.findChild("event",
					"http://jabber.org/protocol/pubsub#event");
			parseEvent(event, packet.getAttribute("from"), account);
		}
		if (packet.hasChild("displayed", "urn:xmpp:chat-markers:0")) {
			String id = packet
					.findChild("displayed", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			String[] fromParts = packet.getAttribute("from").split("/");
			updateLastseen(packet, account, true);
			mXmppConnectionService.markMessage(account, fromParts[0], id,
					Message.STATUS_SEND_DISPLAYED);
		} else if (packet.hasChild("received", "urn:xmpp:chat-markers:0")) {
			String id = packet.findChild("received", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			String[] fromParts = packet.getAttribute("from").split("/");
			updateLastseen(packet, account, false);
			mXmppConnectionService.markMessage(account, fromParts[0], id,
					Message.STATUS_SEND_RECEIVED);
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			Element x = packet.findChild("x",
					"http://jabber.org/protocol/muc#user");
			if (x.hasChild("invite")) {
				Conversation conversation = mXmppConnectionService
						.findOrCreateConversation(account,
								packet.getAttribute("from"), true);
				if (!conversation.getMucOptions().online()) {
					mXmppConnectionService.joinMuc(conversation);
					mXmppConnectionService.updateConversationUi();
				}
			}

		} else if (packet.hasChild("x", "jabber:x:conference")) {
			Element x = packet.findChild("x", "jabber:x:conference");
			String jid = x.getAttribute("jid");
			if (jid != null) {
				Conversation conversation = mXmppConnectionService
						.findOrCreateConversation(account, jid, true);
				if (!conversation.getMucOptions().online()) {
					mXmppConnectionService.joinMuc(conversation);
					mXmppConnectionService.updateConversationUi();
				}
			}
		}
	}

	private void parseEvent(Element event, String from, Account account) {
		Element items = event.findChild("items");
		String node = items.getAttribute("node");
		if (node != null) {
			if (node.equals("urn:xmpp:avatar:metadata")) {
				Avatar avatar = Avatar.parseMetadata(items);
				if (avatar!=null) {
					avatar.owner = from;
					if (mXmppConnectionService.getFileBackend().isAvatarCached(
							avatar)) {
						if (account.getJid().equals(from)) {
							if (account.setAvatar(avatar.getFilename())) {
								mXmppConnectionService.databaseBackend.updateAccount(account);
							}
						} else {
							Contact contact = account.getRoster().getContact(from);
							contact.setAvatar(avatar.getFilename());
						}
					} else {
						mXmppConnectionService.fetchAvatar(account, avatar);
					}
				}
			} else {
				Log.d("xmppService", account.getJid() + ": " + node + " from "
						+ from);
			}
		} else {
			Log.d("xmppService", event.toString());
		}
	}

	private String getPgpBody(Element message) {
		Element child = message.findChild("x", "jabber:x:encrypted");
		if (child == null) {
			return null;
		} else {
			return child.getContent();
		}
	}

	private String getMarkableMessageId(Element message) {
		if (message.hasChild("markable", "urn:xmpp:chat-markers:0")) {
			return message.getAttribute("id");
		} else {
			return null;
		}
	}

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket packet) {
		Message message = null;
		boolean notify = true;
		if (mXmppConnectionService.getPreferences().getBoolean(
				"notification_grace_period_after_carbon_received", true)) {
			notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > XmppConnectionService.CARBON_GRACE_PERIOD;
		}

		if ((packet.getType() == MessagePacket.TYPE_CHAT)) {
			if ((packet.getBody() != null)
					&& (packet.getBody().startsWith("?OTR"))) {
				message = this.parseOtrChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("body")) {
				message = this.parseChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("received") || (packet.hasChild("sent"))) {
				message = this.parseCarbonMessage(packet, account);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_SEND) {
						lastCarbonMessageReceived = SystemClock
								.elapsedRealtime();
						notify = false;
						message.getConversation().markRead();
					} else {
						message.markUnread();
					}
				}
			} else {
				parseNormal(packet, account);
			}

		} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
			message = this.parseGroupchat(packet, account);
			if (message != null) {
				if (message.getStatus() == Message.STATUS_RECEIVED) {
					message.markUnread();
				} else {
					message.getConversation().markRead();
					lastCarbonMessageReceived = SystemClock.elapsedRealtime();
					notify = false;
				}
			}
		} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
			this.parseError(packet, account);
			return;
		} else if (packet.getType() == MessagePacket.TYPE_NORMAL) {
			this.parseNormal(packet, account);
			return;
		} else if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
			this.parseHeadline(packet, account);
			return;
		}
		if ((message == null) || (message.getBody() == null)) {
			return;
		}
		if ((mXmppConnectionService.confirmMessages())
				&& ((packet.getId() != null))) {
			if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
				MessagePacket receipt = mXmppConnectionService
						.getMessageGenerator().received(account, packet,
								"urn:xmpp:chat-markers:0");
				mXmppConnectionService.sendMessagePacket(account, receipt);
			}
			if (packet.hasChild("request", "urn:xmpp:receipts")) {
				MessagePacket receipt = mXmppConnectionService
						.getMessageGenerator().received(account, packet,
								"urn:xmpp:receipts");
				mXmppConnectionService.sendMessagePacket(account, receipt);
			}
		}
		Conversation conversation = message.getConversation();
		conversation.getMessages().add(message);
		if (packet.getType() != MessagePacket.TYPE_ERROR) {
			mXmppConnectionService.databaseBackend.createMessage(message);
		}
		mXmppConnectionService.notifyUi(conversation, notify);
	}

	private void parseHeadline(MessagePacket packet, Account account) {
		if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
			Element event = packet.findChild("event",
					"http://jabber.org/protocol/pubsub#event");
			parseEvent(event, packet.getFrom(), account);
		}
	}
}
