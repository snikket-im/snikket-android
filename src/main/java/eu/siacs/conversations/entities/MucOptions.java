package eu.siacs.conversations.entities;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;

@SuppressLint("DefaultLocale")
public class MucOptions {

	public Account getAccount() {
		return this.conversation.getAccount();
	}

	public void setSelf(User user) {
		this.self = user;
	}

	public enum Affiliation {
		OWNER("owner", 4, R.string.owner),
		ADMIN("admin", 3, R.string.admin),
		MEMBER("member", 2, R.string.member),
		OUTCAST("outcast", 0, R.string.outcast),
		NONE("none", 1, R.string.no_affiliation);

		Affiliation(String string, int rank, int resId) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean outranks(Affiliation affiliation) {
			return rank > affiliation.rank;
		}

		public boolean ranks(Affiliation affiliation) {
			return rank >= affiliation.rank;
		}
	}

	public enum Role {
		MODERATOR("moderator", R.string.moderator,3),
		VISITOR("visitor", R.string.visitor,1),
		PARTICIPANT("participant", R.string.participant,2),
		NONE("none", R.string.no_role,0);

		private Role(String string, int resId, int rank) {
			this.string = string;
			this.resId = resId;
			this.rank = rank;
		}

		private String string;
		private int resId;
		private int rank;

		public int getResId() {
			return resId;
		}

		@Override
		public String toString() {
			return this.string;
		}

		public boolean ranks(Role role) {
			return rank >= role.rank;
		}
	}

	public static final int ERROR_NO_ERROR = 0;
	public static final int ERROR_NICK_IN_USE = 1;
	public static final int ERROR_UNKNOWN = 2;
	public static final int ERROR_PASSWORD_REQUIRED = 3;
	public static final int ERROR_BANNED = 4;
	public static final int ERROR_MEMBERS_ONLY = 5;

	public static final int KICKED_FROM_ROOM = 9;

	public static final String STATUS_CODE_ROOM_CONFIG_CHANGED = "104";
	public static final String STATUS_CODE_SELF_PRESENCE = "110";
	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_CHANGED_NICK = "303";
	public static final String STATUS_CODE_KICKED = "307";
	public static final String STATUS_CODE_LOST_MEMBERSHIP = "321";

	private interface OnEventListener {
		void onSuccess();

		void onFailure();
	}

	public interface OnRenameListener extends OnEventListener {

	}

	public static class User {
		private Role role = Role.NONE;
		private Affiliation affiliation = Affiliation.NONE;
		private Jid jid;
		private Jid fullJid;
		private long pgpKeyId = 0;
		private Avatar avatar;
		private MucOptions options;

		public User(MucOptions options, Jid from) {
			this.options = options;
			this.fullJid = from;
		}

		public String getName() {
			return this.fullJid.getResourcepart();
		}

		public void setJid(Jid jid) {
			this.jid = jid;
		}

		public Jid getJid() {
			return this.jid;
		}

		public Role getRole() {
			return this.role;
		}

		public void setRole(String role) {
			role = role.toLowerCase();
			switch (role) {
				case "moderator":
					this.role = Role.MODERATOR;
					break;
				case "participant":
					this.role = Role.PARTICIPANT;
					break;
				case "visitor":
					this.role = Role.VISITOR;
					break;
				default:
					this.role = Role.NONE;
					break;
			}
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			} else if (!(other instanceof User)) {
				return false;
			} else {
				User o = (User) other;
				return getName() != null && getName().equals(o.getName())
						&& jid != null && jid.equals(o.jid)
						&& affiliation == o.affiliation
						&& role == o.role;
			}
		}

		public Affiliation getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			affiliation = affiliation.toLowerCase();
			switch (affiliation) {
				case "admin":
					this.affiliation = Affiliation.ADMIN;
					break;
				case "owner":
					this.affiliation = Affiliation.OWNER;
					break;
				case "member":
					this.affiliation = Affiliation.MEMBER;
					break;
				case "outcast":
					this.affiliation = Affiliation.OUTCAST;
					break;
				default:
					this.affiliation = Affiliation.NONE;
			}
		}

		public void setPgpKeyId(long id) {
			this.pgpKeyId = id;
		}

		public long getPgpKeyId() {
			return this.pgpKeyId;
		}

		public Contact getContact() {
			return getAccount().getRoster().getContactFromRoster(getJid());
		}

		public boolean setAvatar(Avatar avatar) {
			if (this.avatar != null && this.avatar.equals(avatar)) {
				return false;
			} else {
				this.avatar = avatar;
				return true;
			}
		}

		public String getAvatar() {
			return avatar == null ? null : avatar.getFilename();
		}

		public Account getAccount() {
			return options.getAccount();
		}

		public Jid getFullJid() {
			return fullJid;
		}
	}

	private Account account;
	private final List<User> users = new ArrayList<>();
	private List<String> features = new ArrayList<>();
	private Data form = new Data();
	private Conversation conversation;
	private boolean isOnline = false;
	private int error = ERROR_UNKNOWN;
	public OnRenameListener onRenameListener = null;
	private User self;
	private String subject = null;
	private String password = null;
	public boolean mNickChangingInProgress = false;

	public MucOptions(Conversation conversation) {
		this.account = conversation.getAccount();
		this.conversation = conversation;
		this.self = new User(this,createJoinJid(getProposedNick()));
	}

	public void updateFeatures(ArrayList<String> features) {
		this.features.clear();
		this.features.addAll(features);
	}

	public void updateFormData(Data form) {
		this.form = form;
	}

	public boolean hasFeature(String feature) {
		return this.features.contains(feature);
	}

	public boolean canInvite() {
		Field field = this.form.getFieldByName("muc#roomconfig_allowinvites");
		return !membersOnly() || self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean canChangeSubject() {
		Field field = this.form.getFieldByName("muc#roomconfig_changesubject");
		return self.getRole().ranks(Role.MODERATOR) || (field != null && "1".equals(field.getValue()));
	}

	public boolean participating() {
		return !online() || self.getRole().ranks(Role.PARTICIPANT);
	}

	public boolean membersOnly() {
		return hasFeature("muc_membersonly");
	}

	public boolean mamSupport() {
		// Update with "urn:xmpp:mam:1" once we support it
		return hasFeature("urn:xmpp:mam:0");
	}

	public boolean nonanonymous() {
		return hasFeature("muc_nonanonymous");
	}

	public boolean persistent() {
		return hasFeature("muc_persistent");
	}

	public boolean moderated() {
		return hasFeature("muc_moderated");
	}

	public User deleteUser(String name) {
		synchronized (this.users) {
			for (int i = 0; i < users.size(); ++i) {
				if (users.get(i).getName().equals(name)) {
					return users.remove(i);
				}
			}
		}
		return null;
	}

	public void addUser(User user) {
		synchronized (this.users) {
			for (int i = 0; i < users.size(); ++i) {
				if (users.get(i).getName().equals(user.getName())) {
					users.set(i, user);
					return;
				}
			}
			users.add(user);
		}
	}

	public User findUser(String name) {
		if (name == null) {
			return null;
		}
		synchronized (this.users) {
			for (User user : users) {
				if (user.getName().equals(name)) {
					return user;
				}
			}
		}
		return null;
	}

	public boolean isUserInRoom(String name) {
		return findUser(name) != null;
	}

	public void setError(int error) {
		this.isOnline = error == ERROR_NO_ERROR;
		this.error = error;
	}

	public ArrayList<User> getUsers() {
		synchronized (this.users) {
			return new ArrayList(this.users);
		}
	}

	public List<User> getUsers(int max) {
		synchronized (this.users) {
			return new ArrayList<>(users.subList(0,Math.min(users.size(),5)));
		}
	}

	public int getUserCount() {
		synchronized (this.users) {
			return this.users.size();
		}
	}

	public String getProposedNick() {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getNick() != null
				&& !conversation.getBookmark().getNick().isEmpty()) {
			return conversation.getBookmark().getNick();
		} else if (!conversation.getJid().isBareJid()) {
			return conversation.getJid().getResourcepart();
		} else {
			return account.getUsername();
		}
	}

	public String getActualNick() {
		if (this.self.getName() != null) {
			return this.self.getName();
		} else {
			return this.getProposedNick();
		}
	}

	public boolean online() {
		return this.isOnline;
	}

	public int getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.onRenameListener = listener;
	}

	public void setOffline() {
		this.users.clear();
		this.error = 0;
		this.isOnline = false;
	}

	public User getSelf() {
		return self;
	}

	public void setSubject(String content) {
		this.subject = content;
	}

	public String getSubject() {
		return this.subject;
	}

	public String createNameFromParticipants() {
		synchronized (this.users) {
			if (users.size() >= 2) {
				List<String> names = new ArrayList<String>();
				for (User user : users) {
					Contact contact = user.getContact();
					if (contact != null && !contact.getDisplayName().isEmpty()) {
						names.add(contact.getDisplayName().split("\\s+")[0]);
					} else {
						names.add(user.getName());
					}
				}
				StringBuilder builder = new StringBuilder();
				for (int i = 0; i < names.size(); ++i) {
					builder.append(names.get(i));
					if (i != names.size() - 1) {
						builder.append(", ");
					}
				}
				return builder.toString();
			} else {
				return null;
			}
		}
	}

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<>();
		synchronized (this.users) {
			for (User user : this.users) {
				if (user.getPgpKeyId() != 0) {
					ids.add(user.getPgpKeyId());
				}
			}
		}
		ids.add(account.getPgpId());
		long[] primitiveLongArray = new long[ids.size()];
		for (int i = 0; i < ids.size(); ++i) {
			primitiveLongArray[i] = ids.get(i);
		}
		return primitiveLongArray;
	}

	public boolean pgpKeysInUse() {
		synchronized (this.users) {
			for (User user : this.users) {
				if (user.getPgpKeyId() != 0) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		synchronized (this.users) {
			for (User user : this.users) {
				if (user.getPgpKeyId() == 0) {
					return false;
				}
			}
		}
		return true;
	}

	public Jid createJoinJid(String nick) {
		try {
			return Jid.fromString(this.conversation.getJid().toBareJid().toString() + "/" + nick);
		} catch (final InvalidJidException e) {
			return null;
		}
	}

	public Jid getTrueCounterpart(String counterpart) {
		synchronized (this.users) {
			for (User user : this.users) {
				if (user.getName().equals(counterpart)) {
					return user.getJid();
				}
			}
		}
		return null;
	}

	public String getPassword() {
		this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
		if (this.password == null && conversation.getBookmark() != null
				&& conversation.getBookmark().getPassword() != null) {
			return conversation.getBookmark().getPassword();
		} else {
			return this.password;
		}
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
		conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
	}

	public Conversation getConversation() {
		return this.conversation;
	}
}
