package eu.siacs.conversations.parser;

import android.util.Log;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser extends AbstractParser implements
		OnMessagePacketReceived {
	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private Message parseChat(MessagePacket packet, Account account) {
        final Jid jid = packet.getFrom();
		if (jid == null) {
			return null;
		}
		Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid.toBareJid(), false);
		updateLastseen(packet, account, true);
		String pgpBody = getPgpBody(packet);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation,
					pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
		} else {
			finishedMessage = new Message(conversation,
					packet.getBody(), Message.ENCRYPTION_NONE,
					Message.STATUS_RECEIVED);
		}
		finishedMessage.setRemoteMsgId(packet.getId());
		finishedMessage.markable = isMarkable(packet);
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& !jid.isBareJid()) {
			finishedMessage.setType(Message.TYPE_PRIVATE);
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(jid.getResourcepart()));
			if (conversation.hasDuplicateMessage(finishedMessage)) {
				return null;
			}

		}
		finishedMessage.setCounterpart(jid);
		finishedMessage.setTime(getTimestamp(packet));
		return finishedMessage;
	}

	private Message parseOtrChat(MessagePacket packet, Account account) {
		final Jid to = packet.getTo();
		final Jid from = packet.getFrom();
		if (to == null || from == null) {
			return null;
		}
		boolean properlyAddressed = !to.isBareJid() || account.countPresences() == 1;
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, from.toBareJid(), false);
		String presence;
		if (from.isBareJid()) {
            presence = "";
		} else {
			presence = from.getResourcepart();
		}
		updateLastseen(packet, account, true);
		String body = packet.getBody();
		if (body.matches("^\\?OTRv\\d*\\?")) {
			conversation.endOtrIfNeeded();
		}
		if (!conversation.hasValidOtrSession()) {
			if (properlyAddressed) {
				conversation.startOtrSession(presence,false);
			} else {
				return null;
			}
		} else {
			String foreignPresence = conversation.getOtrSession()
					.getSessionID().getUserID();
			if (!foreignPresence.equals(presence)) {
				conversation.endOtrIfNeeded();
				if (properlyAddressed) {
					conversation.startOtrSession(presence, false);
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
			Message finishedMessage = new Message(conversation, body, Message.ENCRYPTION_OTR,
					Message.STATUS_RECEIVED);
			finishedMessage.setTime(getTimestamp(packet));
			finishedMessage.setRemoteMsgId(packet.getId());
			finishedMessage.markable = isMarkable(packet);
			finishedMessage.setCounterpart(from);
			return finishedMessage;
		} catch (Exception e) {
			conversation.resetOtrSession();
			return null;
		}
	}

	private Message parseGroupchat(MessagePacket packet, Account account) {
		int status;
        final Jid from = packet.getFrom();
		if (from == null) {
			return null;
		}
		if (mXmppConnectionService.find(account.pendingConferenceLeaves,
				account, from.toBareJid()) != null) {
			return null;
		}
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, from.toBareJid(), true);
		if (packet.hasChild("subject")) {
			conversation.getMucOptions().setSubject(
					packet.findChild("subject").getContent());
			mXmppConnectionService.updateConversationUi();
			return null;
		}
		if (from.isBareJid()) {
			return null;
		}
		if (from.getResourcepart().equals(conversation.getMucOptions().getActualNick())) {
			if (mXmppConnectionService.markMessage(conversation,
					packet.getId(), Message.STATUS_SEND)) {
				return null;
			} else if (packet.getId() == null) {
				Message message = conversation.findSentMessageWithBody(packet.getBody());
				if (message != null) {
					mXmppConnectionService.markMessage(message,Message.STATUS_SEND_RECEIVED);
					return null;
				} else {
					status = Message.STATUS_SEND;
				}
			} else {
				status = Message.STATUS_SEND;
			}
		} else {
			status = Message.STATUS_RECEIVED;
		}
		String pgpBody = getPgpBody(packet);
		Message finishedMessage;
		if (pgpBody == null) {
			finishedMessage = new Message(conversation,
					packet.getBody(), Message.ENCRYPTION_NONE, status);
		} else {
			finishedMessage = new Message(conversation, pgpBody,
					Message.ENCRYPTION_PGP, status);
		}
		finishedMessage.setRemoteMsgId(packet.getId());
		finishedMessage.markable = isMarkable(packet);
		finishedMessage.setCounterpart(from);
		if (status == Message.STATUS_RECEIVED) {
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(from.getResourcepart()));
		}
		if (packet.hasChild("delay")
				&& conversation.hasDuplicateMessage(finishedMessage)) {
			return null;
		}
		finishedMessage.setTime(getTimestamp(packet));
		return finishedMessage;
	}

	private Message parseCarbonMessage(final MessagePacket packet, final Account account) {
		int status;
		final Jid fullJid;
		Element forwarded;
		if (packet.hasChild("received", "urn:xmpp:carbons:2")) {
			forwarded = packet.findChild("received", "urn:xmpp:carbons:2")
					.findChild("forwarded", "urn:xmpp:forward:0");
			status = Message.STATUS_RECEIVED;
		} else if (packet.hasChild("sent", "urn:xmpp:carbons:2")) {
			forwarded = packet.findChild("sent", "urn:xmpp:carbons:2")
					.findChild("forwarded", "urn:xmpp:forward:0");
			status = Message.STATUS_SEND;
		} else {
			return null;
		}
		if (forwarded == null) {
			return null;
		}
		Element message = forwarded.findChild("message");
		if (message == null) {
			return null;
		}
		if (!message.hasChild("body")) {
			if (status == Message.STATUS_RECEIVED
					&& message.getAttribute("from") != null) {
				parseNonMessage(message, account);
			} else if (status == Message.STATUS_SEND
					&& message.hasChild("displayed", "urn:xmpp:chat-markers:0")) {
				final Jid to = message.getAttributeAsJid("to");
				if (to != null) {
					final Conversation conversation = mXmppConnectionService.find(
							mXmppConnectionService.getConversations(), account,
							to.toBareJid());
					if (conversation != null) {
						mXmppConnectionService.markRead(conversation, false);
					}
				}
			}
			return null;
		}
		if (status == Message.STATUS_RECEIVED) {
			fullJid = message.getAttributeAsJid("from");
			if (fullJid == null) {
				return null;
			} else {
				updateLastseen(message, account, true);
			}
		} else {
			fullJid = message.getAttributeAsJid("to");
			if (fullJid == null) {
				return null;
			}
		}
		Conversation conversation = mXmppConnectionService
				.findOrCreateConversation(account, fullJid.toBareJid(), false);
		String pgpBody = getPgpBody(message);
		Message finishedMessage;
		if (pgpBody != null) {
			finishedMessage = new Message(conversation, pgpBody,
					Message.ENCRYPTION_PGP, status);
		} else {
			String body = message.findChild("body").getContent();
			finishedMessage = new Message(conversation, body,
					Message.ENCRYPTION_NONE, status);
		}
		finishedMessage.setTime(getTimestamp(message));
		finishedMessage.setRemoteMsgId(message.getAttribute("id"));
		finishedMessage.markable = isMarkable(message);
		finishedMessage.setCounterpart(fullJid);
		if (conversation.getMode() == Conversation.MODE_MULTI
				&& !fullJid.isBareJid()) {
			finishedMessage.setType(Message.TYPE_PRIVATE);
			finishedMessage.setTrueCounterpart(conversation.getMucOptions()
					.getTrueCounterpart(fullJid.getResourcepart()));
			if (conversation.hasDuplicateMessage(finishedMessage)) {
				return null;
			}
		}
		return finishedMessage;
	}

	private Message parseMamMessage(MessagePacket packet, final Account account) {
		final Element result = packet.findChild("result","urn:xmpp:mam:0");
		if (result == null ) {
			return null;
		}
		final MessageArchiveService.Query query = this.mXmppConnectionService.getMessageArchiveService().findQuery(result.getAttribute("queryid"));
		if (query!=null) {
			query.incrementTotalCount();
		}
		final Element forwarded = result.findChild("forwarded","urn:xmpp:forward:0");
		if (forwarded == null) {
			return null;
		}
		final Element message = forwarded.findChild("message");
		if (message == null) {
			return null;
		}
		final Element body = message.findChild("body");
		if (body == null || message.hasChild("private","urn:xmpp:carbons:2") || message.hasChild("no-copy","urn:xmpp:hints")) {
			return null;
		}
		int encryption;
		String content = getPgpBody(message);
		if (content != null) {
			encryption = Message.ENCRYPTION_PGP;
		} else {
			encryption = Message.ENCRYPTION_NONE;
			content = body.getContent();
		}
		if (content == null) {
			return null;
		}
		final long timestamp = getTimestamp(forwarded);
		final Jid to = message.getAttributeAsJid("to");
		final Jid from = message.getAttributeAsJid("from");
		Jid counterpart;
		int status;
		Conversation conversation;
		if (from!=null && to != null && from.toBareJid().equals(account.getJid().toBareJid())) {
			status = Message.STATUS_SEND;
			conversation = this.mXmppConnectionService.findOrCreateConversation(account,to.toBareJid(),false,query);
			counterpart = to;
		} else if (from !=null && to != null) {
			status = Message.STATUS_RECEIVED;
			conversation = this.mXmppConnectionService.findOrCreateConversation(account,from.toBareJid(),false,query);
			counterpart = from;
		} else {
			return null;
		}
		Message finishedMessage = new Message(conversation,content,encryption,status);
		finishedMessage.setTime(timestamp);
		finishedMessage.setCounterpart(counterpart);
		finishedMessage.setRemoteMsgId(message.getAttribute("id"));
		finishedMessage.setServerMsgId(result.getAttribute("id"));
		if (conversation.hasDuplicateMessage(finishedMessage)) {
			return null;
		}
		if (query!=null) {
			query.incrementMessageCount();
		}
		return finishedMessage;
	}

	private void parseError(final MessagePacket packet, final Account account) {
		final Jid from = packet.getFrom();
		mXmppConnectionService.markMessage(account, from.toBareJid(),
				packet.getId(), Message.STATUS_SEND_FAILED);
	}

	private void parseNonMessage(Element packet, Account account) {
		final Jid from = packet.getAttributeAsJid("from");
		if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
			Element event = packet.findChild("event",
					"http://jabber.org/protocol/pubsub#event");
			parseEvent(event, from, account);
		} else if (from != null
				&& packet.hasChild("displayed", "urn:xmpp:chat-markers:0")) {
			String id = packet
					.findChild("displayed", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			updateLastseen(packet, account, true);
			mXmppConnectionService.markMessage(account, from.toBareJid(),
					id, Message.STATUS_SEND_DISPLAYED);
		} else if (from != null
				&& packet.hasChild("received", "urn:xmpp:chat-markers:0")) {
			String id = packet.findChild("received", "urn:xmpp:chat-markers:0")
					.getAttribute("id");
			updateLastseen(packet, account, false);
			mXmppConnectionService.markMessage(account, from.toBareJid(),
					id, Message.STATUS_SEND_RECEIVED);
		} else if (from != null
				&& packet.hasChild("received", "urn:xmpp:receipts")) {
			String id = packet.findChild("received", "urn:xmpp:receipts")
					.getAttribute("id");
			updateLastseen(packet, account, false);
			mXmppConnectionService.markMessage(account, from.toBareJid(),
					id, Message.STATUS_SEND_RECEIVED);
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			Element x = packet.findChild("x",
					"http://jabber.org/protocol/muc#user");
			if (x.hasChild("invite")) {
				Conversation conversation = mXmppConnectionService
						.findOrCreateConversation(account,
								packet.getAttributeAsJid("from"), true);
				if (!conversation.getMucOptions().online()) {
					if (x.hasChild("password")) {
						Element password = x.findChild("password");
						conversation.getMucOptions().setPassword(
								password.getContent());
						mXmppConnectionService.databaseBackend
								.updateConversation(conversation);
					}
					mXmppConnectionService.joinMuc(conversation);
					mXmppConnectionService.updateConversationUi();
				}
			}
		} else if (packet.hasChild("x", "jabber:x:conference")) {
			Element x = packet.findChild("x", "jabber:x:conference");
            Jid jid = x.getAttributeAsJid("jid");
            String password = x.getAttribute("password");
			if (jid != null) {
				Conversation conversation = mXmppConnectionService
						.findOrCreateConversation(account, jid, true);
				if (!conversation.getMucOptions().online()) {
					if (password != null) {
						conversation.getMucOptions().setPassword(password);
						mXmppConnectionService.databaseBackend
								.updateConversation(conversation);
					}
					mXmppConnectionService.joinMuc(conversation);
					mXmppConnectionService.updateConversationUi();
				}
			}
		}
	}

	private void parseEvent(final Element event, final Jid from, final Account account) {
		Element items = event.findChild("items");
		if (items == null) {
			return;
		}
		String node = items.getAttribute("node");
		if (node == null) {
			return;
		}
		if (node.equals("urn:xmpp:avatar:metadata")) {
			Avatar avatar = Avatar.parseMetadata(items);
			if (avatar != null) {
				avatar.owner = from;
				if (mXmppConnectionService.getFileBackend().isAvatarCached(
						avatar)) {
					if (account.getJid().toBareJid().equals(from)) {
						if (account.setAvatar(avatar.getFilename())) {
							mXmppConnectionService.databaseBackend
									.updateAccount(account);
						}
						mXmppConnectionService.getAvatarService().clear(
								account);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateAccountUi();
					} else {
						Contact contact = account.getRoster().getContact(
								from);
						contact.setAvatar(avatar.getFilename());
						mXmppConnectionService.getAvatarService().clear(
								contact);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateRosterUi();
					}
				} else {
					mXmppConnectionService.fetchAvatar(account, avatar);
				}
			}
		} else if (node.equals("http://jabber.org/protocol/nick")) {
			Element item = items.findChild("item");
			if (item != null) {
				Element nick = item.findChild("nick",
						"http://jabber.org/protocol/nick");
				if (nick != null) {
					if (from != null) {
						Contact contact = account.getRoster().getContact(
								from);
						contact.setPresenceName(nick.getContent());
						mXmppConnectionService.getAvatarService().clear(account);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateAccountUi();
					}
				}
			}
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

	private boolean isMarkable(Element message) {
		return message.hasChild("markable", "urn:xmpp:chat-markers:0");
	}

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket packet) {
		Message message = null;
		this.parseNick(packet, account);

		if ((packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_NORMAL)) {
			if ((packet.getBody() != null)
					&& (packet.getBody().startsWith("?OTR"))) {
				message = this.parseOtrChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("body")
					&& !(packet.hasChild("x",
					"http://jabber.org/protocol/muc#user"))) {
				message = this.parseChat(packet, account);
				if (message != null) {
					message.markUnread();
				}
			} else if (packet.hasChild("received", "urn:xmpp:carbons:2")
					|| (packet.hasChild("sent", "urn:xmpp:carbons:2"))) {
				message = this.parseCarbonMessage(packet, account);
				if (message != null) {
					if (message.getStatus() == Message.STATUS_SEND) {
						account.activateGracePeriod();
						mXmppConnectionService.markRead(
								message.getConversation(), false);
					} else {
						message.markUnread();
					}
				}
			} else if (packet.hasChild("result","urn:xmpp:mam:0")) {
				message = parseMamMessage(packet, account);
				if (message != null) {
					Conversation conversation = message.getConversation();
					conversation.add(message);
					mXmppConnectionService.databaseBackend.createMessage(message);
				}
				return;
			} else if (packet.hasChild("fin","urn:xmpp:mam:0")) {
				Element fin = packet.findChild("fin","urn:xmpp:mam:0");
				mXmppConnectionService.getMessageArchiveService().processFin(fin);
			} else {
				parseNonMessage(packet, account);
			}
		} else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
			message = this.parseGroupchat(packet, account);
			if (message != null) {
				if (message.getStatus() == Message.STATUS_RECEIVED) {
					message.markUnread();
				} else {
					mXmppConnectionService.markRead(message.getConversation(),
							false);
					account.activateGracePeriod();
				}
			}
		} else if (packet.getType() == MessagePacket.TYPE_ERROR) {
			this.parseError(packet, account);
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
		conversation.add(message);
		if (account.getXmppConnection() != null && account.getXmppConnection().getFeatures().advancedStreamFeaturesLoaded()) {
			if (conversation.setLastMessageTransmitted(System.currentTimeMillis())) {
				mXmppConnectionService.updateConversation(conversation);
			}
		}

		if (message.getStatus() == Message.STATUS_RECEIVED
				&& conversation.getOtrSession() != null
				&& !conversation.getOtrSession().getSessionID().getUserID()
				.equals(message.getCounterpart().getResourcepart())) {
			conversation.endOtrIfNeeded();
		}

		if (packet.getType() != MessagePacket.TYPE_ERROR) {
			if (message.getEncryption() == Message.ENCRYPTION_NONE
					|| mXmppConnectionService.saveEncryptedMessages()) {
				mXmppConnectionService.databaseBackend.createMessage(message);
			}
		}
		if (message.trusted() && message.bodyContainsDownloadable()) {
			this.mXmppConnectionService.getHttpConnectionManager()
					.createNewConnection(message);
		} else if (!message.isRead()) {
			mXmppConnectionService.getNotificationService().push(message);
		}
		mXmppConnectionService.updateConversationUi();
	}

	private void parseHeadline(MessagePacket packet, Account account) {
		if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
			Element event = packet.findChild("event",
					"http://jabber.org/protocol/pubsub#event");
			parseEvent(event, packet.getFrom(), account);
		}
	}

	private void parseNick(MessagePacket packet, Account account) {
		Element nick = packet.findChild("nick",
				"http://jabber.org/protocol/nick");
		if (nick != null) {
			if (packet.getFrom() != null) {
				Contact contact = account.getRoster().getContact(
						packet.getFrom());
				contact.setPresenceName(nick.getContent());
			}
		}
	}
}
