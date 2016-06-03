package eu.siacs.conversations.entities;

import android.annotation.SuppressLint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.forms.Field;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.Avatar;

@SuppressLint("DefaultLocale")
public class MucOptions {

	private boolean mAutoPushConfiguration = true;

	public Account getAccount() {
		return this.conversation.getAccount();
	}

	public void setSelf(User user) {
		this.self = user;
	}

	public void changeAffiliation(Jid jid, Affiliation affiliation) {
		User user = findUserByRealJid(jid);
		synchronized (users) {
			if (user != null && user.getRole() == Role.NONE) {
				users.remove(user);
				if (affiliation.ranks(Affiliation.MEMBER)) {
					user.affiliation = affiliation;
					users.add(user);
				}
			}
		}
	}

	public void flagNoAutoPushConfiguration() {
		mAutoPushConfiguration = false;
	}

	public boolean autoPushConfiguration() {
		return mAutoPushConfiguration;
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

		Role(String string, int resId, int rank) {
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

	public enum Error {
		NO_RESPONSE,
		NONE,
		NICK_IN_USE,
		PASSWORD_REQUIRED,
		BANNED,
		MEMBERS_ONLY,
		KICKED,
		SHUTDOWN,
		UNKNOWN
	}

	public static final String STATUS_CODE_ROOM_CONFIG_CHANGED = "104";
	public static final String STATUS_CODE_SELF_PRESENCE = "110";
	public static final String STATUS_CODE_ROOM_CREATED = "201";
	public static final String STATUS_CODE_BANNED = "301";
	public static final String STATUS_CODE_CHANGED_NICK = "303";
	public static final String STATUS_CODE_KICKED = "307";
	public static final String STATUS_CODE_AFFILIATION_CHANGE = "321";
	public static final String STATUS_CODE_LOST_MEMBERSHIP = "322";
	public static final String STATUS_CODE_SHUTDOWN = "332";

	private interface OnEventListener {
		void onSuccess();

		void onFailure();
	}

	public interface OnRenameListener extends OnEventListener {

	}

	public static class User implements Comparable<User> {
		private Role role = Role.NONE;
		private Affiliation affiliation = Affiliation.NONE;
		private Jid realJid;
		private Jid fullJid;
		private long pgpKeyId = 0;
		private Avatar avatar;
		private MucOptions options;

		public User(MucOptions options, Jid from) {
			this.options = options;
			this.fullJid = from;
		}

		public String getName() {
			return fullJid == null ? null : fullJid.getResourcepart();
		}

		public void setRealJid(Jid jid) {
			this.realJid = jid != null ? jid.toBareJid() : null;
		}

		public Role getRole() {
			return this.role;
		}

		public void setRole(String role) {
			if (role == null) {
				this.role = Role.NONE;
				return;
			}
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

		public Affiliation getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			if (affiliation == null) {
				this.affiliation = Affiliation.NONE;
				return;
			}
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
			if (fullJid != null) {
				return getAccount().getRoster().getContactFromRoster(realJid);
			} else if (realJid != null){
				return getAccount().getRoster().getContact(realJid);
			} else {
				return null;
			}
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

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			User user = (User) o;

			if (role != user.role) return false;
			if (affiliation != user.affiliation) return false;
			if (realJid != null ? !realJid.equals(user.realJid) : user.realJid != null)
				return false;
			return fullJid != null ? fullJid.equals(user.fullJid) : user.fullJid == null;

		}

		@Override
		public int hashCode() {
			int result = role != null ? role.hashCode() : 0;
			result = 31 * result + (affiliation != null ? affiliation.hashCode() : 0);
			result = 31 * result + (realJid != null ? realJid.hashCode() : 0);
			result = 31 * result + (fullJid != null ? fullJid.hashCode() : 0);
			return result;
		}

		@Override
		public String toString() {
			return "[fulljid:"+String.valueOf(fullJid)+",realjid:"+String.valueOf(realJid)+",affiliation"+affiliation.toString()+"]";
		}

		public boolean realJidMatchesAccount() {
			return realJid != null && realJid.equals(options.account.getJid().toBareJid());
		}

		@Override
		public int compareTo(User another) {
			Contact ourContact = getContact();
			Contact anotherContact = another.getContact();
			if (ourContact != null && anotherContact != null) {
				return ourContact.compareTo(anotherContact);
			} else if (ourContact == null && anotherContact != null) {
				return getName().compareToIgnoreCase(anotherContact.getDisplayName());
			} else if (ourContact != null) {
				return ourContact.getDisplayName().compareToIgnoreCase(another.getName());
			} else {
				return getName().compareToIgnoreCase(another.getName());
			}
		}

		public Jid getRealJid() {
			return realJid;
		}
	}

	private Account account;
	private final Set<User> users = new HashSet<>();
	private final List<String> features = new ArrayList<>();
	private Data form = new Data();
	private Conversation conversation;
	private boolean isOnline = false;
	private Error error = Error.NONE;
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
		return !online()
				|| self.getRole().ranks(Role.PARTICIPANT)
				|| hasFeature("muc_unmoderated");
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

	public User deleteUser(Jid jid) {
		User user = findUserByFullJid(jid);
		if (user != null) {
			synchronized (users) {
				users.remove(user);
				if (user.affiliation.ranks(Affiliation.MEMBER) && user.realJid != null) {
					user.role = Role.NONE;
					user.avatar = null;
					user.fullJid = null;
					users.add(user);
				}
			}
		}
		return user;
	}

	public void addUser(User user) {
		User old;
		if (user.fullJid == null && user.realJid != null) {
			old = findUserByRealJid(user.realJid);
			if (old != null) {
				if (old.fullJid != null) {
					return; //don't add. user already exists
				} else {
					synchronized (users) {
						users.remove(old);
					}
				}
			}
		} else if (user.realJid != null) {
			old = findUserByRealJid(user.realJid);
			synchronized (users) {
				if (old != null && old.fullJid == null) {
					users.remove(old);
				}
			}
		}
		old = findUserByFullJid(user.getFullJid());
		synchronized (this.users) {
			if (old != null) {
				users.remove(old);
			}
			this.users.add(user);
		}
	}

	public User findUserByFullJid(Jid jid) {
		if (jid == null) {
			return null;
		}
		synchronized (users) {
			for (User user : users) {
				if (jid.equals(user.getFullJid())) {
					return user;
				}
			}
		}
		return null;
	}

	public User findUserByRealJid(Jid jid) {
		if (jid == null) {
			return null;
		}
		synchronized (users) {
			for (User user : users) {
				if (jid.equals(user.realJid)) {
					return user;
				}
			}
		}
		return null;
	}

	public boolean isUserInRoom(Jid jid) {
		return findUserByFullJid(jid) != null;
	}

	public void setError(Error error) {
		this.isOnline = isOnline && error == Error.NONE;
		this.error = error;
	}

	public void setOnline() {
		this.isOnline = true;
	}

	public ArrayList<User> getUsers() {
		return getUsers(true);
	}

	public ArrayList<User> getUsers(boolean includeOffline) {
		synchronized (users) {
			if (includeOffline) {
				return new ArrayList<>(users);
			} else {
				ArrayList<User> onlineUsers = new ArrayList<>();
				for (User user : users) {
					if (user.getRole().ranks(Role.PARTICIPANT)) {
						onlineUsers.add(user);
					}
				}
				return onlineUsers;
			}
		}
	}

	public List<User> getUsers(int max) {
		ArrayList<User> users = getUsers();
		return users.subList(0, Math.min(max, users.size()));
	}

	public int getUserCount() {
		synchronized (users) {
			return users.size();
		}
	}

	public String getProposedNick() {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getNick() != null
				&& !conversation.getBookmark().getNick().trim().isEmpty()) {
			return conversation.getBookmark().getNick().trim();
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

	public Error getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.onRenameListener = listener;
	}

	public void setOffline() {
		synchronized (users) {
			this.users.clear();
		}
		this.error = Error.NO_RESPONSE;
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
		if (getUserCount() >= 2) {
			List<String> names = new ArrayList<>();
			for (User user : getUsers(5)) {
				Contact contact = user.getContact();
				if (contact != null && !contact.getDisplayName().isEmpty()) {
					names.add(contact.getDisplayName().split("\\s+")[0]);
				} else if (user.getName() != null){
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

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<>();
		for (User user : this.users) {
			if (user.getPgpKeyId() != 0) {
				ids.add(user.getPgpKeyId());
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
		synchronized (users) {
			for (User user : users) {
				if (user.getPgpKeyId() != 0) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		synchronized (users) {
			for (User user : users) {
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

	public Jid getTrueCounterpart(Jid jid) {
		if (jid.equals(getSelf().getFullJid())) {
			return account.getJid().toBareJid();
		}
		User user = findUserByFullJid(jid);
		return user == null ? null : user.realJid;
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

	public List<Jid> getMembers() {
		ArrayList<Jid> members = new ArrayList<>();
		synchronized (users) {
			for (User user : users) {
				if (user.affiliation.ranks(Affiliation.MEMBER) && user.realJid != null) {
					members.add(user.realJid);
				}
			}
		}
		return members;
	}
}
