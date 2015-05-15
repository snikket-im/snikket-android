package eu.siacs.conversations.parser;

import android.util.Log;
import android.util.Pair;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser extends AbstractParser implements
		OnMessagePacketReceived {
	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private boolean extractChatState(Conversation conversation, final MessagePacket packet) {
		ChatState state = ChatState.parse(packet);
		if (state != null && conversation != null) {
			final Account account = conversation.getAccount();
			Jid from = packet.getFrom();
			if (from.toBareJid().equals(account.getJid().toBareJid())) {
				conversation.setOutgoingChatState(state);
				return false;
			} else {
				return conversation.setIncomingChatState(state);
			}
		}
		return false;
	}

	private Message parseOtrChat(String body, Jid from, String id, Conversation conversation) {
		String presence;
		if (from.isBareJid()) {
			presence = "";
		} else {
			presence = from.getResourcepart();
		}
		if (body.matches("^\\?OTRv\\d{1,2}\\?.*")) {
			conversation.endOtrIfNeeded();
		}
		if (!conversation.hasValidOtrSession()) {
			conversation.startOtrSession(presence,false);
		} else {
			String foreignPresence = conversation.getOtrSession().getSessionID().getUserID();
			if (!foreignPresence.equals(presence)) {
				conversation.endOtrIfNeeded();
				conversation.startOtrSession(presence, false);
			}
		}
		try {
			conversation.setLastReceivedOtrMessageId(id);
			Session otrSession = conversation.getOtrSession();
			SessionStatus before = otrSession.getSessionStatus();
			body = otrSession.transformReceiving(body);
			SessionStatus after = otrSession.getSessionStatus();
			if ((before != after) && (after == SessionStatus.ENCRYPTED)) {
				conversation.setNextEncryption(Message.ENCRYPTION_OTR);
				mXmppConnectionService.onOtrSessionEstablished(conversation);
			} else if ((before != after) && (after == SessionStatus.FINISHED)) {
				conversation.setNextEncryption(Message.ENCRYPTION_NONE);
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
			Message finishedMessage = new Message(conversation, body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
			conversation.setLastReceivedOtrMessageId(null);
			return finishedMessage;
		} catch (Exception e) {
			conversation.resetOtrSession();
			return null;
		}
	}

	private class Invite {
		Jid jid;
		String password;
		Invite(Jid jid, String password) {
			this.jid = jid;
			this.password = password;
		}
	}

	private Invite extractInvite(Element message) {
		Element x = message.findChild("x", "http://jabber.org/protocol/muc#user");
		if (x != null) {
			Element invite = x.findChild("invite");
			if (invite != null) {
				Element pw = x.findChild("password");
				return new Invite(message.getAttributeAsJid("from"), pw != null ? pw.getContent(): null);
			}
		} else {
			x = message.findChild("x","jabber:x:conference");
			if (x != null) {
				return new Invite(x.getAttributeAsJid("jid"),x.getAttribute("password"));
			}
		}
		return null;
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
						contact.setAvatar(avatar);
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

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket original) {
		final MessagePacket packet;
		Long timestamp = null;
		final boolean isForwarded;
		boolean carbon = false; //live carbons or mam-sub
		if (original.fromServer(account)) {
			Pair<MessagePacket, Long> f;
			f = original.getForwardedMessagePacket("received", "urn:xmpp:carbons:2");
			f = f == null ? original.getForwardedMessagePacket("sent", "urn:xmpp:carbons:2") : f;
			f = f == null ? original.getForwardedMessagePacket("result", "urn:xmpp:mam:0") : f;
			packet = f != null ? f.first : original;
			timestamp = f != null ? f.second : null;
			isForwarded = f != null;
			carbon = original.hasChild("received", "urn:xmpp:carbons:2") || original.hasChild("received", "urn:xmpp:carbons:2");

			Element fin = packet.findChild("fin", "urn:xmpp:mam:0");
			if (fin != null) {
				mXmppConnectionService.getMessageArchiveService().processFin(fin);
				return;
			}

		} else {
			packet = original;
			isForwarded = false;
		}
		if (timestamp == null) {
			timestamp = AbstractParser.getTimestamp(packet, System.currentTimeMillis());
		}
		final String body = packet.getBody();
		final String encrypted = packet.findChildContent("x", "jabber:x:encrypted");
		int status;
		final Jid to = packet.getTo();
		final Jid from = packet.getFrom();
		final Jid counterpart;
		final String id = packet.getId();
		boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
		boolean properlyAddressed = !to.isBareJid() || account.countPresences() == 1;
		if (packet.fromAccount(account)) {
			status = Message.STATUS_SEND;
			counterpart = to;
		} else {
			status = Message.STATUS_RECEIVED;
			counterpart = from;
		}

		if (from == null || to == null) {
			Log.d(Config.LOGTAG,"no to or from in: "+packet.toString());
			return;
		}

		Invite invite = extractInvite(packet);
		if (invite != null && invite.jid != null) {
			Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, invite.jid, true);
			if (!conversation.getMucOptions().online()) {
				conversation.getMucOptions().setPassword(invite.password);
				mXmppConnectionService.databaseBackend.updateConversation(conversation);
				mXmppConnectionService.joinMuc(conversation);
				mXmppConnectionService.updateConversationUi();
			}
			return;
		}

		if (extractChatState(mXmppConnectionService.find(account,from), packet)) {
			mXmppConnectionService.updateConversationUi();
		}

		if (body != null || encrypted != null) {
			Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat);
			if (isTypeGroupChat) {
				if (counterpart.getResourcepart().equals(conversation.getMucOptions().getActualNick())) {
					status = Message.STATUS_SEND;
					if (mXmppConnectionService.markMessage(conversation, id, Message.STATUS_SEND_RECEIVED)) {
						return;
					} else if (id == null) {
						Message message = conversation.findSentMessageWithBody(packet.getBody());
						if (message != null) {
							mXmppConnectionService.markMessage(message, Message.STATUS_SEND_RECEIVED);
							return;
						}
					}
				} else {
					status = Message.STATUS_RECEIVED;
				}
			}
			Message message;
			if (body != null && body.startsWith("?OTR")) {
				if (!isForwarded && !isTypeGroupChat && properlyAddressed) {
					message = parseOtrChat(body, from, id, conversation);
					if (message == null) {
						return;
					}
				} else {
					message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
				}
			} else if (encrypted != null) {
				message = new Message(conversation, encrypted, Message.ENCRYPTION_PGP, status);
			} else {
				message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
			}
			message.setCounterpart(counterpart);
			message.setRemoteMsgId(id);
			message.setTime(timestamp);
			message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				message.setTrueCounterpart(conversation.getMucOptions().getTrueCounterpart(counterpart.getResourcepart()));
				if (!isTypeGroupChat) {
					message.setType(Message.TYPE_PRIVATE);
				}
			}
			updateLastseen(packet,account,true);
			conversation.add(message);
			if (carbon || status == Message.STATUS_RECEIVED) {
				mXmppConnectionService.markRead(conversation);
				account.activateGracePeriod();
			} else if (!isForwarded) {
				message.markUnread();
			}


			if (mXmppConnectionService.confirmMessages() && id != null && !packet.fromAccount(account)) {
				if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
					MessagePacket receipt = mXmppConnectionService
							.getMessageGenerator().received(account, packet, "urn:xmpp:chat-markers:0");
					mXmppConnectionService.sendMessagePacket(account, receipt);
				}
				if (packet.hasChild("request", "urn:xmpp:receipts")) {
					MessagePacket receipt = mXmppConnectionService
							.getMessageGenerator().received(account, packet, "urn:xmpp:receipts");
					mXmppConnectionService.sendMessagePacket(account, receipt);
				}
			}
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

			if (message.getEncryption() == Message.ENCRYPTION_NONE
					|| mXmppConnectionService.saveEncryptedMessages()) {
				mXmppConnectionService.databaseBackend.createMessage(message);
			}
			final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
			if (message.trusted() && message.bodyContainsDownloadable() && manager.getAutoAcceptFileSize() > 0) {
				manager.createNewConnection(message);
			} else if (!message.isRead()) {
				mXmppConnectionService.getNotificationService().push(message);
			}
			mXmppConnectionService.updateConversationUi();
		} else {
			if (packet.hasChild("subject") && isTypeGroupChat) {
				Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
				if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
					conversation.setHasMessagesLeftOnServer(true);
					conversation.getMucOptions().setSubject(packet.findChildContent("subject"));
					mXmppConnectionService.updateConversationUi();
					return;
				}
			}
		}

		Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
		if (received == null) {
			received = packet.findChild("received", "urn:xmpp:receipts");
		}
		if (received != null && !packet.fromAccount(account)) {
			mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
		}
		Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
		if (displayed != null) {
			if (packet.fromAccount(account)) {
				Conversation conversation = mXmppConnectionService.find(account,counterpart.toBareJid());
				mXmppConnectionService.markRead(conversation);
			} else {
				updateLastseen(packet, account, true);
				final Message displayedMessage = mXmppConnectionService.markMessage(account, from.toBareJid(), displayed.getAttribute("id"), Message.STATUS_SEND_DISPLAYED);
				Message message = displayedMessage == null ? null : displayedMessage.prev();
				while (message != null
						&& message.getStatus() == Message.STATUS_SEND_RECEIVED
						&& message.getTimeSent() < displayedMessage.getTimeSent()) {
					mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
					message = message.prev();
				}
			}
		}

		Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
		if (event != null) {
			parseEvent(event, from, account);
		}

		String nick = packet.findChildContent("nick", "http://jabber.org/protocol/nick");
		if (nick != null) {
			Contact contact = account.getRoster().getContact(from);
			contact.setPresenceName(nick);
		}
	}
}