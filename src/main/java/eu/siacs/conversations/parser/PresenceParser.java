package eu.siacs.conversations.parser;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class PresenceParser extends AbstractParser implements
		OnPresencePacketReceived {

	public PresenceParser(XmppConnectionService service) {
		super(service);
	}

	public void parseConferencePresence(PresencePacket packet, Account account) {
		final Conversation conversation = packet.getFrom() == null ? null : mXmppConnectionService.find(account, packet.getFrom().toBareJid());
		if (conversation != null) {
			final MucOptions mucOptions = conversation.getMucOptions();
			boolean before = mucOptions.online();
			int count = mucOptions.getUserCount();
			final List<MucOptions.User> tileUserBefore = mucOptions.getUsers(5);
			processConferencePresence(packet, mucOptions);
			final List<MucOptions.User> tileUserAfter = mucOptions.getUsers(5);
			if (!tileUserAfter.equals(tileUserBefore)) {
				mXmppConnectionService.getAvatarService().clear(mucOptions);
			}
			if (before != mucOptions.online() || (mucOptions.online() && count != mucOptions.getUserCount())) {
				mXmppConnectionService.updateConversationUi();
			} else if (mucOptions.online()) {
				mXmppConnectionService.updateMucRosterUi();
			}
		}
	}

	private void processConferencePresence(PresencePacket packet, MucOptions mucOptions) {
		final Jid from = packet.getFrom();
		if (!from.isBareJid()) {
			final String type = packet.getAttribute("type");
			final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
			Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
			final List<String> codes = getStatusCodes(x);
			if (type == null) {
				if (x != null) {
					Element item = x.findChild("item");
					if (item != null && !from.isBareJid()) {
						MucOptions.User user = new MucOptions.User(mucOptions,from);
						user.setAffiliation(item.getAttribute("affiliation"));
						user.setRole(item.getAttribute("role"));
						user.setJid(item.getAttributeAsJid("jid"));
						if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(mucOptions.getConversation().getJid())) {
							mucOptions.setError(MucOptions.ERROR_NO_ERROR);
							mucOptions.setSelf(user);
							if (mucOptions.mNickChangingInProgress) {
								if (mucOptions.onRenameListener != null) {
									mucOptions.onRenameListener.onSuccess();
								}
								mucOptions.mNickChangingInProgress = false;
							}
						} else {
							mucOptions.addUser(user);
						}
						if (mXmppConnectionService.getPgpEngine() != null) {
							Element signed = packet.findChild("x", "jabber:x:signed");
							if (signed != null) {
								Element status = packet.findChild("status");
								String msg = status == null ? "" : status.getContent();
								long keyId = mXmppConnectionService.getPgpEngine().fetchKeyId(mucOptions.getAccount(), msg, signed.getContent());
								if (keyId != 0) {
									user.setPgpKeyId(keyId);
								}
							}
						}
						if (avatar != null) {
							avatar.owner = from;
							if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
								if (user.setAvatar(avatar)) {
									mXmppConnectionService.getAvatarService().clear(user);
								}
							} else {
								mXmppConnectionService.fetchAvatar(mucOptions.getAccount(), avatar);
							}
						}
					}
				}
			} else if (type.equals("unavailable")) {
				if (codes.contains(MucOptions.STATUS_CODE_SELF_PRESENCE) ||
						packet.getFrom().equals(mucOptions.getConversation().getJid())) {
					if (codes.contains(MucOptions.STATUS_CODE_CHANGED_NICK)) {
						mucOptions.mNickChangingInProgress = true;
					} else if (codes.contains(MucOptions.STATUS_CODE_KICKED)) {
						mucOptions.setError(MucOptions.KICKED_FROM_ROOM);
					} else if (codes.contains(MucOptions.STATUS_CODE_BANNED)) {
						mucOptions.setError(MucOptions.ERROR_BANNED);
					} else if (codes.contains(MucOptions.STATUS_CODE_LOST_MEMBERSHIP)) {
						mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
					} else {
						mucOptions.setError(MucOptions.ERROR_UNKNOWN);
					}
				} else if (!from.isBareJid()){
					MucOptions.User user = mucOptions.deleteUser(from.getResourcepart());
					if (user != null) {
						mXmppConnectionService.getAvatarService().clear(user);
					}
				}
			} else if (type.equals("error")) {
				Element error = packet.findChild("error");
				if (error != null && error.hasChild("conflict")) {
					if (mucOptions.online()) {
						if (mucOptions.onRenameListener != null) {
							mucOptions.onRenameListener.onFailure();
						}
					} else {
						mucOptions.setError(MucOptions.ERROR_NICK_IN_USE);
					}
				} else if (error != null && error.hasChild("not-authorized")) {
					mucOptions.setError(MucOptions.ERROR_PASSWORD_REQUIRED);
				} else if (error != null && error.hasChild("forbidden")) {
					mucOptions.setError(MucOptions.ERROR_BANNED);
				} else if (error != null && error.hasChild("registration-required")) {
					mucOptions.setError(MucOptions.ERROR_MEMBERS_ONLY);
				}
			}
		}
	}

	private static List<String> getStatusCodes(Element x) {
		List<String> codes = new ArrayList<>();
		if (x != null) {
			for (Element child : x.getChildren()) {
				if (child.getName().equals("status")) {
					String code = child.getAttribute("code");
					if (code != null) {
						codes.add(code);
					}
				}
			}
		}
		return codes;
	}

	public void parseContactPresence(final PresencePacket packet, final Account account) {
		final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
		final Jid from = packet.getFrom();
		if (from == null) {
			return;
		}
		final String type = packet.getAttribute("type");
		final Contact contact = account.getRoster().getContact(from);
		if (type == null) {
			String presence = from.isBareJid() ? "" : from.getResourcepart();
			contact.setPresenceName(packet.findChildContent("nick", "http://jabber.org/protocol/nick"));
			Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
			if (avatar != null && !contact.isSelf()) {
				avatar.owner = from.toBareJid();
				if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
					if (contact.setAvatar(avatar)) {
						mXmppConnectionService.getAvatarService().clear(contact);
						mXmppConnectionService.updateConversationUi();
						mXmppConnectionService.updateRosterUi();
					}
				} else {
					mXmppConnectionService.fetchAvatar(account, avatar);
				}
			}
			int sizeBefore = contact.getPresences().size();
			contact.updatePresence(presence, Presences.parseShow(packet.findChild("show")));
			PgpEngine pgp = mXmppConnectionService.getPgpEngine();
			Element x = packet.findChild("x", "jabber:x:signed");
			if (pgp != null && x != null) {
				Element status = packet.findChild("status");
				String msg = status != null ? status.getContent() : "";
				contact.setPgpKeyId(pgp.fetchKeyId(account, msg, x.getContent()));
			}
			boolean online = sizeBefore < contact.getPresences().size();
			updateLastseen(packet, account, false);
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
		} else if (type.equals("unavailable")) {
			if (from.isBareJid()) {
				contact.clearPresences();
			} else {
				contact.removePresence(from.getResourcepart());
			}
			mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
		} else if (type.equals("subscribe")) {
			if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
				mXmppConnectionService.sendPresencePacket(account,
						mPresenceGenerator.sendPresenceUpdatesTo(contact));
			} else {
				contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
				final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
						account, contact.getJid().toBareJid(), false);
				final String statusMessage = packet.findChildContent("status");
				if (statusMessage != null
						&& !statusMessage.isEmpty()
						&& conversation.countMessages() == 0) {
					conversation.add(new Message(
							conversation,
							statusMessage,
							Message.ENCRYPTION_NONE,
							Message.STATUS_RECEIVED
					));
				}
			}
		}
		mXmppConnectionService.updateRosterUi();
	}

	@Override
	public void onPresencePacketReceived(Account account, PresencePacket packet) {
		if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			this.parseConferencePresence(packet, account);
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
			this.parseConferencePresence(packet, account);
		} else {
			this.parseContactPresence(packet, account);
		}
	}

}
