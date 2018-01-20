package eu.siacs.conversations.parser;

import android.os.Build;
import android.text.Html;
import android.util.Log;
import android.util.Pair;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrService;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.ReadByMarker;
import eu.siacs.conversations.entities.ReceiptRequest;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.chatstate.ChatState;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

	private static final List<String> CLIENTS_SENDING_HTML_IN_OTR = Arrays.asList("Pidgin", "Adium", "Trillian");

	public MessageParser(XmppConnectionService service) {
		super(service);
	}

	private boolean extractChatState(Conversation c, final boolean isTypeGroupChat, final MessagePacket packet) {
		ChatState state = ChatState.parse(packet);
		if (state != null && c != null) {
			final Account account = c.getAccount();
			Jid from = packet.getFrom();
			if (from.toBareJid().equals(account.getJid().toBareJid())) {
				c.setOutgoingChatState(state);
				if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
					mXmppConnectionService.markRead(c);
					activateGracePeriod(account);
				}
				return false;
			} else {
				if (isTypeGroupChat) {
					MucOptions.User user = c.getMucOptions().findUserByFullJid(from);
					if (user != null) {
						return user.setChatState(state);
					} else {
						return false;
					}
				} else {
					return c.setIncomingChatState(state);
				}
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
			conversation.startOtrSession(presence, false);
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
			body = otrSession.transformReceiving(body);
			SessionStatus status = otrSession.getSessionStatus();
			if (body == null && status == SessionStatus.ENCRYPTED) {
				mXmppConnectionService.onOtrSessionEstablished(conversation);
				return null;
			} else if (body == null && status == SessionStatus.FINISHED) {
				conversation.resetOtrSession();
				mXmppConnectionService.updateConversationUi();
				return null;
			} else if (body == null || (body.isEmpty())) {
				return null;
			}
			if (body.startsWith(CryptoHelper.FILETRANSFER)) {
				String key = body.substring(CryptoHelper.FILETRANSFER.length());
				conversation.setSymmetricKey(CryptoHelper.hexToBytes(key));
				return null;
			}
			if (clientMightSendHtml(conversation.getAccount(), from)) {
				Log.d(Config.LOGTAG, conversation.getAccount().getJid().toBareJid() + ": received OTR message from bad behaving client. escaping HTML…");
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					body = Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString();
				} else {
					body = Html.fromHtml(body).toString();
				}
			}

			final OtrService otrService = conversation.getAccount().getOtrService();
			Message finishedMessage = new Message(conversation, body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
			finishedMessage.setFingerprint(otrService.getFingerprint(otrSession.getRemotePublicKey()));
			conversation.setLastReceivedOtrMessageId(null);

			return finishedMessage;
		} catch (Exception e) {
			conversation.resetOtrSession();
			return null;
		}
	}

	private static boolean clientMightSendHtml(Account account, Jid from) {
		String resource = from.getResourcepart();
		if (resource == null) {
			return false;
		}
		Presence presence = account.getRoster().getContact(from).getPresences().getPresences().get(resource);
		ServiceDiscoveryResult disco = presence == null ? null : presence.getServiceDiscoveryResult();
		if (disco == null) {
			return false;
		}
		return hasIdentityKnowForSendingHtml(disco.getIdentities());
	}

	private static boolean hasIdentityKnowForSendingHtml(List<ServiceDiscoveryResult.Identity> identities) {
		for (ServiceDiscoveryResult.Identity identity : identities) {
			if (identity.getName() != null) {
				if (CLIENTS_SENDING_HTML_IN_OTR.contains(identity.getName())) {
					return true;
				}
			}
		}
		return false;
	}

	private Message parseAxolotlChat(Element axolotlMessage, Jid from, Conversation conversation, int status, boolean postpone) {
		final AxolotlService service = conversation.getAccount().getAxolotlService();
		final XmppAxolotlMessage xmppAxolotlMessage;
		try {
			xmppAxolotlMessage = XmppAxolotlMessage.fromElement(axolotlMessage, from.toBareJid());
		} catch (Exception e) {
			Log.d(Config.LOGTAG, conversation.getAccount().getJid().toBareJid() + ": invalid omemo message received " + e.getMessage());
			return null;
		}
		if (xmppAxolotlMessage.hasPayload()) {
			final XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = service.processReceivingPayloadMessage(xmppAxolotlMessage, postpone);
			if (plaintextMessage != null) {
				Message finishedMessage = new Message(conversation, plaintextMessage.getPlaintext(), Message.ENCRYPTION_AXOLOTL, status);
				finishedMessage.setFingerprint(plaintextMessage.getFingerprint());
				Log.d(Config.LOGTAG, AxolotlService.getLogprefix(finishedMessage.getConversation().getAccount()) + " Received Message with session fingerprint: " + plaintextMessage.getFingerprint());
				return finishedMessage;
			}
		} else {
			Log.d(Config.LOGTAG,conversation.getAccount().getJid().toBareJid()+": received OMEMO key transport message");
			service.processReceivingKeyTransportMessage(xmppAxolotlMessage, postpone);
		}
		return null;
	}

	private class Invite {
		final Jid jid;
		final String password;
		final Contact inviter;

		Invite(Jid jid, String password, Contact inviter) {
			this.jid = jid;
			this.password = password;
			this.inviter = inviter;
		}

		public boolean execute(Account account) {
			if (jid != null) {
				Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, jid, true, false);
				if (!conversation.getMucOptions().online()) {
					conversation.getMucOptions().setPassword(password);
					mXmppConnectionService.databaseBackend.updateConversation(conversation);
					mXmppConnectionService.joinMuc(conversation, inviter != null && inviter.mutualPresenceSubscription());
					mXmppConnectionService.updateConversationUi();
				}
				return true;
			}
			return false;
		}
	}

	private Invite extractInvite(Account account, Element message) {
		Element x = message.findChild("x", "http://jabber.org/protocol/muc#user");
		if (x != null) {
			Element invite = x.findChild("invite");
			if (invite != null) {
				Element pw = x.findChild("password");
				Jid from = invite.getAttributeAsJid("from");
				Contact contact = from == null ? null : account.getRoster().getContact(from);
				return new Invite(message.getAttributeAsJid("from"), pw != null ? pw.getContent() : null, contact);
			}
		} else {
			x = message.findChild("x", "jabber:x:conference");
			if (x != null) {
				Jid from = message.getAttributeAsJid("from");
				Contact contact = from == null ? null : account.getRoster().getContact(from);
				return new Invite(x.getAttributeAsJid("jid"), x.getAttribute("password"), contact);
			}
		}
		return null;
	}

	private static String extractStanzaId(Element packet, boolean isTypeGroupChat, Conversation conversation) {
		final Jid by;
		final boolean safeToExtract;
		if (isTypeGroupChat) {
			by = conversation.getJid().toBareJid();
			safeToExtract = conversation.getMucOptions().hasFeature(Namespace.STANZA_IDS);
		} else {
			Account account = conversation.getAccount();
			by = account.getJid().toBareJid();
			safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
		}
		return safeToExtract ? extractStanzaId(packet, by) : null;
	}

	private static String extractStanzaId(Element packet, Jid by) {
		for (Element child : packet.getChildren()) {
			if (child.getName().equals("stanza-id")
					&& Namespace.STANZA_IDS.equals(child.getNamespace())
					&& by.equals(child.getAttributeAsJid("by"))) {
				return child.getAttribute("id");
			}
		}
		return null;
	}

	private void parseEvent(final Element event, final Jid from, final Account account) {
		Element items = event.findChild("items");
		String node = items == null ? null : items.getAttribute("node");
		if ("urn:xmpp:avatar:metadata".equals(node)) {
			Avatar avatar = Avatar.parseMetadata(items);
			if (avatar != null) {
				avatar.owner = from.toBareJid();
				if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
					if (account.getJid().toBareJid().equals(from)) {
						if (account.setAvatar(avatar.getFilename())) {
							mXmppConnectionService.databaseBackend.updateAccount(account);
						}
						mXmppConnectionService.getAvatarService().clear(account);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateAccountUi();
					} else {
						Contact contact = account.getRoster().getContact(from);
						contact.setAvatar(avatar);
						mXmppConnectionService.getAvatarService().clear(contact);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateRosterUi();
					}
				} else if (mXmppConnectionService.isDataSaverDisabled()) {
					mXmppConnectionService.fetchAvatar(account, avatar);
				}
			}
		} else if ("http://jabber.org/protocol/nick".equals(node)) {
			final Element i = items.findChild("item");
			final String nick = i == null ? null : i.findChildContent("nick", Namespace.NICK);
			if (nick != null) {
				Contact contact = account.getRoster().getContact(from);
				if (contact.setPresenceName(nick)) {
					mXmppConnectionService.getAvatarService().clear(contact);
				}
				mXmppConnectionService.updateConversationUi();
				mXmppConnectionService.updateAccountUi();
			}
		} else if (AxolotlService.PEP_DEVICE_LIST.equals(node)) {
			Element item = items.findChild("item");
			Set<Integer> deviceIds = mXmppConnectionService.getIqParser().deviceIds(item);
			Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received PEP device list " + deviceIds + " update from " + from + ", processing... ");
			AxolotlService axolotlService = account.getAxolotlService();
			axolotlService.registerDevices(from, deviceIds);
			mXmppConnectionService.updateAccountUi();
		}
	}

	private boolean handleErrorMessage(Account account, MessagePacket packet) {
		if (packet.getType() == MessagePacket.TYPE_ERROR) {
			Jid from = packet.getFrom();
			if (from != null) {
				Message message = mXmppConnectionService.markMessage(account,
						from.toBareJid(),
						packet.getId(),
						Message.STATUS_SEND_FAILED,
						extractErrorMessage(packet));
				if (message != null) {
					if (message.getEncryption() == Message.ENCRYPTION_OTR) {
						message.getConversation().endOtrIfNeeded();
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void onMessagePacketReceived(Account account, MessagePacket original) {
		if (handleErrorMessage(account, original)) {
			return;
		}
		final MessagePacket packet;
		Long timestamp = null;
		final boolean isForwarded;
		boolean isCarbon = false;
		String serverMsgId = null;
		final Element fin = original.findChild("fin", Namespace.MAM_LEGACY);
		if (fin != null) {
			mXmppConnectionService.getMessageArchiveService().processFinLegacy(fin, original.getFrom());
			return;
		}
		final boolean mamLegacy = original.hasChild("result", Namespace.MAM_LEGACY);
		final Element result = original.findChild("result", mamLegacy ? Namespace.MAM_LEGACY : Namespace.MAM);
		final MessageArchiveService.Query query = result == null ? null : mXmppConnectionService.getMessageArchiveService().findQuery(result.getAttribute("queryid"));
		if (query != null && query.validFrom(original.getFrom())) {
			Pair<MessagePacket, Long> f = original.getForwardedMessagePacket("result", mamLegacy ? Namespace.MAM_LEGACY : Namespace.MAM);
			if (f == null) {
				return;
			}
			timestamp = f.second;
			packet = f.first;
			isForwarded = true;
			serverMsgId = result.getAttribute("id");
			query.incrementMessageCount();
		} else if (query != null) {
			Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received mam result from invalid sender");
			return;
		} else if (original.fromServer(account)) {
			Pair<MessagePacket, Long> f;
			f = original.getForwardedMessagePacket("received", "urn:xmpp:carbons:2");
			f = f == null ? original.getForwardedMessagePacket("sent", "urn:xmpp:carbons:2") : f;
			packet = f != null ? f.first : original;
			if (handleErrorMessage(account, packet)) {
				return;
			}
			timestamp = f != null ? f.second : null;
			isCarbon = f != null;
			isForwarded = isCarbon;
		} else {
			packet = original;
			isForwarded = false;
		}

		if (timestamp == null) {
			timestamp = AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
		}
		final String body = packet.getBody();
		final Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");
		final String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");
		final Element replaceElement = packet.findChild("replace", "urn:xmpp:message-correct:0");
		final Element oob = packet.findChild("x", Namespace.OOB);
		final String oobUrl = oob != null ? oob.findChildContent("url") : null;
		final String replacementId = replaceElement == null ? null : replaceElement.getAttribute("id");
		final Element axolotlEncrypted = packet.findChild(XmppAxolotlMessage.CONTAINERTAG, AxolotlService.PEP_PREFIX);
		int status;
		final Jid counterpart;
		final Jid to = packet.getTo();
		final Jid from = packet.getFrom();
		final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
		final String remoteMsgId;
		if (originId != null && originId.getAttribute("id") != null) {
			remoteMsgId = originId.getAttribute("id");
		} else {
			remoteMsgId = packet.getId();
		}
		boolean notify = false;

		if (from == null) {
			Log.d(Config.LOGTAG, "no from in: " + packet.toString());
			return;
		}

		boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
		if (query != null && !query.muc() && isTypeGroupChat) {
			Log.e(Config.LOGTAG,account.getJid().toBareJid()+": received groupchat ("+from+") message on regular MAM request. skipping");
			return;
		}
		boolean isProperlyAddressed = (to != null) && (!to.isBareJid() || account.countPresences() == 0);
		boolean isMucStatusMessage = from.isBareJid() && mucUserElement != null && mucUserElement.hasChild("status");
		if (packet.fromAccount(account)) {
			status = Message.STATUS_SEND;
			counterpart = to != null ? to : account.getJid();
		} else {
			status = Message.STATUS_RECEIVED;
			counterpart = from;
		}

		Invite invite = extractInvite(account, packet);
		if (invite != null && invite.execute(account)) {
			return;
		}

		if ((body != null || pgpEncrypted != null || axolotlEncrypted != null || oobUrl != null) && !isMucStatusMessage) {
			final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat, false, query, false);
			final boolean conversationMultiMode = conversation.getMode() == Conversation.MODE_MULTI;

			if (serverMsgId == null) {
				serverMsgId = extractStanzaId(packet, isTypeGroupChat, conversation);
			}

			if (isTypeGroupChat) {
				if (conversation.getMucOptions().isSelf(counterpart)) {
					status = Message.STATUS_SEND_RECEIVED;
					isCarbon = true; //not really carbon but received from another resource
					if (mXmppConnectionService.markMessage(conversation, remoteMsgId, status, serverMsgId)) {
						return;
					} else if (remoteMsgId == null || Config.IGNORE_ID_REWRITE_IN_MUC) {
						Message message = conversation.findSentMessageWithBody(packet.getBody());
						if (message != null) {
							mXmppConnectionService.markMessage(message, status);
							return;
						}
					}
				} else {
					status = Message.STATUS_RECEIVED;
				}
			}
			final Message message;
			if (body != null && body.startsWith("?OTR") && Config.supportOtr()) {
				if (!isForwarded && !isTypeGroupChat && isProperlyAddressed && !conversationMultiMode) {
					message = parseOtrChat(body, from, remoteMsgId, conversation);
					if (message == null) {
						return;
					}
				} else {
					Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": ignoring OTR message from " + from + " isForwarded=" + Boolean.toString(isForwarded) + ", isProperlyAddressed=" + Boolean.valueOf(isProperlyAddressed));
					message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
				}
			} else if (pgpEncrypted != null && Config.supportOpenPgp()) {
				message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP, status);
			} else if (axolotlEncrypted != null && Config.supportOmemo()) {
				Jid origin;
				if (conversationMultiMode) {
					final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
					origin = getTrueCounterpart(query != null ? mucUserElement : null, fallback);
					if (origin == null) {
						Log.d(Config.LOGTAG, "axolotl message in non anonymous conference received");
						return;
					}
				} else {
					origin = from;
				}
				message = parseAxolotlChat(axolotlEncrypted, origin, conversation, status, query != null);
				if (message == null) {
					if (query == null &&  extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), isTypeGroupChat, packet)) {
						mXmppConnectionService.updateConversationUi();
					}
					if (query != null && status == Message.STATUS_SEND && remoteMsgId != null) {
						Message previouslySent = conversation.findSentMessageWithUuid(remoteMsgId);
						if (previouslySent != null && previouslySent.getServerMsgId() == null && serverMsgId != null) {
							previouslySent.setServerMsgId(serverMsgId);
							mXmppConnectionService.databaseBackend.updateMessage(previouslySent);
							Log.d(Config.LOGTAG,account.getJid().toBareJid()+": encountered previously sent OMEMO message without serverId. updating...");
						}
					}
					return;
				}
				if (conversationMultiMode) {
					message.setTrueCounterpart(origin);
				}
			} else if (body == null && oobUrl != null) {
				message = new Message(conversation, oobUrl, Message.ENCRYPTION_NONE, status);
				message.setOob(true);
				if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				}
			} else {
				message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
			}

			message.setCounterpart(counterpart);
			message.setRemoteMsgId(remoteMsgId);
			message.setServerMsgId(serverMsgId);
			message.setCarbon(isCarbon);
			message.setTime(timestamp);
			if (body != null && body.equals(oobUrl)) {
				message.setOob(true);
				if (CryptoHelper.isPgpEncryptedUrl(oobUrl)) {
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
				}
			}
			message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
			if (conversationMultiMode) {
				final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
				Jid trueCounterpart;
				if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
					trueCounterpart = message.getTrueCounterpart();
				} else if (query != null && query.safeToExtractTrueCounterpart()) {
					trueCounterpart = getTrueCounterpart(mucUserElement, fallback);
				} else {
					trueCounterpart = fallback;
				}
				if (trueCounterpart != null && trueCounterpart.toBareJid().equals(account.getJid().toBareJid())) {
					status = isTypeGroupChat ? Message.STATUS_SEND_RECEIVED : Message.STATUS_SEND;
				}
				message.setStatus(status);
				message.setTrueCounterpart(trueCounterpart);
				if (!isTypeGroupChat) {
					message.setType(Message.TYPE_PRIVATE);
				}
			} else {
				updateLastseen(account, from);
			}

			if (replacementId != null && mXmppConnectionService.allowMessageCorrection()) {
				final Message replacedMessage = conversation.findMessageWithRemoteIdAndCounterpart(replacementId,
						counterpart,
						message.getStatus() == Message.STATUS_RECEIVED,
						message.isCarbon());
				if (replacedMessage != null) {
					final boolean fingerprintsMatch = replacedMessage.getFingerprint() == null
							|| replacedMessage.getFingerprint().equals(message.getFingerprint());
					final boolean trueCountersMatch = replacedMessage.getTrueCounterpart() != null
							&& replacedMessage.getTrueCounterpart().equals(message.getTrueCounterpart());
					final boolean duplicate = conversation.hasDuplicateMessage(message);
					if (fingerprintsMatch && (trueCountersMatch || !conversationMultiMode) && !duplicate) {
						Log.d(Config.LOGTAG, "replaced message '" + replacedMessage.getBody() + "' with '" + message.getBody() + "'");
						synchronized (replacedMessage) {
							final String uuid = replacedMessage.getUuid();
							replacedMessage.setUuid(UUID.randomUUID().toString());
							replacedMessage.setBody(message.getBody());
							replacedMessage.setEdited(replacedMessage.getRemoteMsgId());
							replacedMessage.setRemoteMsgId(remoteMsgId);
							if (replacedMessage.getServerMsgId() == null || message.getServerMsgId() != null) {
								replacedMessage.setServerMsgId(message.getServerMsgId());
							}
							replacedMessage.setEncryption(message.getEncryption());
							if (replacedMessage.getStatus() == Message.STATUS_RECEIVED) {
								replacedMessage.markUnread();
							}
							extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), isTypeGroupChat, packet);
							mXmppConnectionService.updateMessage(replacedMessage, uuid);
							mXmppConnectionService.getNotificationService().updateNotification(false);
							if (mXmppConnectionService.confirmMessages()
									&& replacedMessage.getStatus() == Message.STATUS_RECEIVED
									&& (replacedMessage.trusted() || replacedMessage.getType() == Message.TYPE_PRIVATE)
									&& remoteMsgId != null
									&& (!isForwarded || query != null)
									&& !isTypeGroupChat) {
								processMessageReceipts(account, packet, query);
							}
							if (replacedMessage.getEncryption() == Message.ENCRYPTION_PGP) {
								conversation.getAccount().getPgpDecryptionService().discard(replacedMessage);
								conversation.getAccount().getPgpDecryptionService().decrypt(replacedMessage, false);
							}
						}
						return;
					} else {
						Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": received message correction but verification didn't check out");
					}
				}
			}

			long deletionDate = mXmppConnectionService.getAutomaticMessageDeletionDate();
			if (deletionDate != 0 && message.getTimeSent() < deletionDate) {
				Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": skipping message from " + message.getCounterpart().toString() + " because it was sent prior to our deletion date");
				return;
			}

			boolean checkForDuplicates = (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
					|| message.getType() == Message.TYPE_PRIVATE
					|| message.getServerMsgId() != null;
			if (checkForDuplicates ) {
				final Message duplicate = conversation.findDuplicateMessage(message);
				if (duplicate != null) {
					final boolean serverMsgIdUpdated;
					if (duplicate.getStatus() != Message.STATUS_RECEIVED
							&& duplicate.getUuid().equals(message.getRemoteMsgId())
							&& duplicate.getServerMsgId() == null
							&& message.getServerMsgId() != null) {
						duplicate.setServerMsgId(message.getServerMsgId());
						mXmppConnectionService.databaseBackend.updateMessage(message);
						serverMsgIdUpdated = true;
					} else {
						serverMsgIdUpdated = false;
					}
					Log.d(Config.LOGTAG, "skipping duplicate message with " + message.getCounterpart()+". serverMsgIdUpdated="+Boolean.toString(serverMsgIdUpdated));
					return;
				}
			}

			if (query != null && query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
				conversation.prepend(message);
			} else {
				conversation.add(message);
			}
			if (query != null) {
				query.incrementActualMessageCount();
			}

			if (query == null || query.isCatchup()) { //either no mam or catchup
				if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
					mXmppConnectionService.markRead(conversation);
					if (query == null) {
						activateGracePeriod(account);
					}
				} else {
					message.markUnread();
					notify = true;
				}
			}

			if (message.getEncryption() == Message.ENCRYPTION_PGP) {
				notify = conversation.getAccount().getPgpDecryptionService().decrypt(message, notify);
			}

			if (query == null) {
				extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), isTypeGroupChat, packet);
				mXmppConnectionService.updateConversationUi();
			}

			if (mXmppConnectionService.confirmMessages()
					&& message.getStatus() == Message.STATUS_RECEIVED
					&& (message.trusted() || message.getType() == Message.TYPE_PRIVATE)
					&& remoteMsgId != null
					&& (!isForwarded || query != null)
					&& !isTypeGroupChat) {
				processMessageReceipts(account, packet, query);
			}

			if (message.getStatus() == Message.STATUS_RECEIVED
					&& conversation.getOtrSession() != null
					&& !conversation.getOtrSession().getSessionID().getUserID()
					.equals(message.getCounterpart().getResourcepart())) {
				conversation.endOtrIfNeeded();
			}

			mXmppConnectionService.databaseBackend.createMessage(message);
			final HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
			if (message.trusted() && message.treatAsDownloadable() && manager.getAutoAcceptFileSize() > 0) {
				manager.createNewDownloadConnection(message);
			} else if (notify) {
				if (query != null && query.isCatchup()) {
					mXmppConnectionService.getNotificationService().pushFromBacklog(message);
				} else {
					mXmppConnectionService.getNotificationService().push(message);
				}
			}
		} else if (!packet.hasChild("body")) { //no body

			if (query == null && extractChatState(mXmppConnectionService.find(account, counterpart.toBareJid()), isTypeGroupChat, packet)) {
				mXmppConnectionService.updateConversationUi();
			}

			final Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
			if (isTypeGroupChat) {
				if (packet.hasChild("subject")) {
					if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
						conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
						String subject = packet.findChildContent("subject");
						if (conversation.getMucOptions().setSubject(subject)) {
							mXmppConnectionService.updateConversation(conversation);
						}
						final Bookmark bookmark = conversation.getBookmark();
						if (bookmark != null && bookmark.getBookmarkName() == null) {
							if (bookmark.setBookmarkName(subject)) {
								mXmppConnectionService.pushBookmarks(account);
							}
						}
						mXmppConnectionService.updateConversationUi();
						return;
					}
				}
			}
			if (conversation != null && mucUserElement != null && from.isBareJid()) {
				for (Element child : mucUserElement.getChildren()) {
					if ("status".equals(child.getName())) {
						try {
							int code = Integer.parseInt(child.getAttribute("code"));
							if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
								mXmppConnectionService.fetchConferenceConfiguration(conversation);
								break;
							}
						} catch (Exception e) {
							//ignored
						}
					} else if ("item".equals(child.getName())) {
						MucOptions.User user = AbstractParser.parseItem(conversation, child);
						Log.d(Config.LOGTAG, account.getJid() + ": changing affiliation for "
								+ user.getRealJid() + " to " + user.getAffiliation() + " in "
								+ conversation.getJid().toBareJid());
						if (!user.realJidMatchesAccount()) {
							boolean isNew = conversation.getMucOptions().updateUser(user);
							mXmppConnectionService.getAvatarService().clear(conversation);
							mXmppConnectionService.updateMucRosterUi();
							mXmppConnectionService.updateConversationUi();
							if (!user.getAffiliation().ranks(MucOptions.Affiliation.MEMBER)) {
								Jid jid = user.getRealJid();
								List<Jid> cryptoTargets = conversation.getAcceptedCryptoTargets();
								if (cryptoTargets.remove(user.getRealJid())) {
									Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": removed " + jid + " from crypto targets of " + conversation.getName());
									conversation.setAcceptedCryptoTargets(cryptoTargets);
									mXmppConnectionService.updateConversation(conversation);
								}
							} else if (isNew && user.getRealJid() != null && account.getAxolotlService().hasEmptyDeviceList(user.getRealJid())) {
								account.getAxolotlService().fetchDeviceIds(user.getRealJid());
							}
						}
					}
				}
			}
		}

		Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
		if (received == null) {
			received = packet.findChild("received", "urn:xmpp:receipts");
		}
		if (received != null) {
			String id = received.getAttribute("id");
			if (packet.fromAccount(account)) {
				if (query != null && id != null && packet.getTo() != null) {
					query.pendingReceiptRequests.remove(new ReceiptRequest(packet.getTo(),id));
				}
			} else {
				mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
			}
		}
		Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
		if (displayed != null) {
			final String id = displayed.getAttribute("id");
			final Jid sender = displayed.getAttributeAsJid("sender");
			if (packet.fromAccount(account)) {
				Conversation conversation = mXmppConnectionService.find(account, counterpart.toBareJid());
				if (conversation != null && (query == null || query.isCatchup())) {
					mXmppConnectionService.markRead(conversation); //TODO only mark messages read that are older than timestamp
				}
			} else if (isTypeGroupChat) {
				Conversation conversation = mXmppConnectionService.find(account, counterpart.toBareJid());
				if (conversation != null && id != null && sender != null) {
					Message message = conversation.findMessageWithRemoteId(id, sender);
					if (message != null) {
						final Jid fallback = conversation.getMucOptions().getTrueCounterpart(counterpart);
						final Jid trueJid = getTrueCounterpart((query != null && query.safeToExtractTrueCounterpart()) ? mucUserElement : null, fallback);
						final boolean trueJidMatchesAccount = account.getJid().toBareJid().equals(trueJid == null ? null : trueJid.toBareJid());
						if (trueJidMatchesAccount || conversation.getMucOptions().isSelf(counterpart)) {
							if (!message.isRead() && (query == null || query.isCatchup())) { //checking if message is unread fixes race conditions with reflections
								mXmppConnectionService.markRead(conversation);
							}
						} else  if (!counterpart.isBareJid() && trueJid != null){
							ReadByMarker readByMarker = ReadByMarker.from(counterpart, trueJid);
							if (message.addReadByMarker(readByMarker)) {
								Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": added read by (" + readByMarker.getRealJid() + ") to message '" + message.getBody() + "'");
								mXmppConnectionService.updateMessage(message);
							}
						}
					}
				}
			} else {
				final Message displayedMessage = mXmppConnectionService.markMessage(account, from.toBareJid(), id, Message.STATUS_SEND_DISPLAYED);
				Message message = displayedMessage == null ? null : displayedMessage.prev();
				while (message != null
						&& message.getStatus() == Message.STATUS_SEND_RECEIVED
						&& message.getTimeSent() < displayedMessage.getTimeSent()) {
					mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
					message = message.prev();
				}
			}
		}

		Element event = original.findChild("event", "http://jabber.org/protocol/pubsub#event");
		if (event != null) {
			parseEvent(event, original.getFrom(), account);
		}

		final String nick = packet.findChildContent("nick", Namespace.NICK);
		if (nick != null) {
			Contact contact = account.getRoster().getContact(from);
			if (contact.setPresenceName(nick)) {
				mXmppConnectionService.getAvatarService().clear(contact);
			}
		}
	}

	private static Jid getTrueCounterpart(Element mucUserElement, Jid fallback) {
		final Element item = mucUserElement == null ? null : mucUserElement.findChild("item");
		Jid result = item == null ? null : item.getAttributeAsJid("jid");
		return result != null ? result : fallback;
	}

	private void processMessageReceipts(Account account, MessagePacket packet, MessageArchiveService.Query query) {
		final boolean markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
		final boolean request = packet.hasChild("request", "urn:xmpp:receipts");
		if (query == null) {
			final ArrayList<String> receiptsNamespaces = new ArrayList<>();
			if (markable) {
				receiptsNamespaces.add("urn:xmpp:chat-markers:0");
			}
			if (request) {
				receiptsNamespaces.add("urn:xmpp:receipts");
			}
			if (receiptsNamespaces.size() > 0) {
				MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
						packet,
						receiptsNamespaces,
						packet.getType());
				mXmppConnectionService.sendMessagePacket(account, receipt);
			}
		} else {
			if (request) {
				query.pendingReceiptRequests.add(new ReceiptRequest(packet.getFrom(),packet.getId()));
			}
		}
	}

	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

	private void activateGracePeriod(Account account) {
		long duration = mXmppConnectionService.getLongPreference("grace_period_length", R.integer.grace_period) * 1000;
		Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": activating grace period till " + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
		account.activateGracePeriod(duration);
	}
}
