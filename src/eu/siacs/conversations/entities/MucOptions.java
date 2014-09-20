package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import android.annotation.SuppressLint;

@SuppressLint("DefaultLocale")
public class MucOptions {
	public static final int ERROR_NO_ERROR = 0;
	public static final int ERROR_NICK_IN_USE = 1;
	public static final int ERROR_ROOM_NOT_FOUND = 2;
	public static final int ERROR_PASSWORD_REQUIRED = 3;

	public interface OnRenameListener {
		public void onRename(boolean success);
	}

	public class User {
		public static final int ROLE_MODERATOR = 3;
		public static final int ROLE_NONE = 0;
		public static final int ROLE_PARTICIPANT = 2;
		public static final int ROLE_VISITOR = 1;
		public static final int AFFILIATION_ADMIN = 4;
		public static final int AFFILIATION_OWNER = 3;
		public static final int AFFILIATION_MEMBER = 2;
		public static final int AFFILIATION_OUTCAST = 1;
		public static final int AFFILIATION_NONE = 0;

		private int role;
		private int affiliation;
		private String name;
		private String jid;
		private long pgpKeyId = 0;

		public String getName() {
			return name;
		}

		public void setName(String user) {
			this.name = user;
		}

		public void setJid(String jid) {
			this.jid = jid;
		}

		public String getJid() {
			return this.jid;
		}

		public int getRole() {
			return this.role;
		}

		public void setRole(String role) {
			role = role.toLowerCase();
			if (role.equals("moderator")) {
				this.role = ROLE_MODERATOR;
			} else if (role.equals("participant")) {
				this.role = ROLE_PARTICIPANT;
			} else if (role.equals("visitor")) {
				this.role = ROLE_VISITOR;
			} else {
				this.role = ROLE_NONE;
			}
		}

		public int getAffiliation() {
			return this.affiliation;
		}

		public void setAffiliation(String affiliation) {
			if (affiliation.equalsIgnoreCase("admin")) {
				this.affiliation = AFFILIATION_ADMIN;
			} else if (affiliation.equalsIgnoreCase("owner")) {
				this.affiliation = AFFILIATION_OWNER;
			} else if (affiliation.equalsIgnoreCase("member")) {
				this.affiliation = AFFILIATION_MEMBER;
			} else if (affiliation.equalsIgnoreCase("outcast")) {
				this.affiliation = AFFILIATION_OUTCAST;
			} else {
				this.affiliation = AFFILIATION_NONE;
			}
		}

		public void setPgpKeyId(long id) {
			this.pgpKeyId = id;
		}

		public long getPgpKeyId() {
			return this.pgpKeyId;
		}
	}

	private Account account;
	private List<User> users = new CopyOnWriteArrayList<User>();
	private Conversation conversation;
	private boolean isOnline = false;
	private int error = ERROR_ROOM_NOT_FOUND;
	private OnRenameListener renameListener = null;
	private boolean aboutToRename = false;
	private User self = new User();
	private String subject = null;
	private String joinnick;
	private String password = null;
	private boolean passwordChanged = false;

	public MucOptions(Account account) {
		this.account = account;
	}

	public void deleteUser(String name) {
		for (int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(name)) {
				users.remove(i);
				return;
			}
		}
	}

	public void addUser(User user) {
		for (int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(user.getName())) {
				users.set(i, user);
				return;
			}
		}
		users.add(user);
	}

	public void processPacket(PresencePacket packet, PgpEngine pgp) {
		String[] fromParts = packet.getFrom().split("/",2);
		if (fromParts.length >= 2) {
			String name = fromParts[1];
			String type = packet.getAttribute("type");
			if (type == null) {
				User user = new User();
				Element item = packet.findChild("x",
						"http://jabber.org/protocol/muc#user")
						.findChild("item");
				user.setName(name);
				user.setAffiliation(item.getAttribute("affiliation"));
				user.setRole(item.getAttribute("role"));
				user.setJid(item.getAttribute("jid"));
				user.setName(name);
				if (name.equals(this.joinnick)) {
					this.isOnline = true;
					this.error = ERROR_NO_ERROR;
					self = user;
					if (aboutToRename) {
						if (renameListener != null) {
							renameListener.onRename(true);
						}
						aboutToRename = false;
					}
					if (conversation.getBookmark() != null
							&& conversation.getBookmark().isProvidePassword()) {
						this.passwordChanged = false;
					}
				} else {
					addUser(user);
				}
				if (pgp != null) {
					Element x = packet.findChild("x", "jabber:x:signed");
					if (x != null) {
						Element status = packet.findChild("status");
						String msg;
						if (status != null) {
							msg = status.getContent();
						} else {
							msg = "";
						}
						user.setPgpKeyId(pgp.fetchKeyId(account, msg,
								x.getContent()));
					}
				}
			} else if (type.equals("unavailable")) {
				deleteUser(packet.getAttribute("from").split("/",2)[1]);
			} else if (type.equals("error")) {
				Element error = packet.findChild("error");
				if (error.hasChild("conflict")) {
					if (aboutToRename) {
						if (renameListener != null) {
							renameListener.onRename(false);
						}
						aboutToRename = false;
						this.setJoinNick(getActualNick());
					} else {
						this.error = ERROR_NICK_IN_USE;
					}
				} else if (error.hasChild("not-authorized")) {
					if (conversation.getBookmark() != null
							&& conversation.getBookmark().isProvidePassword()) {
						this.passwordChanged = true;
					}
					this.error = ERROR_PASSWORD_REQUIRED;
				}
			}
		}
	}

	public List<User> getUsers() {
		return this.users;
	}

	public String getProposedNick() {
		String[] mucParts = conversation.getContactJid().split("/",2);
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getNick() != null) {
			return conversation.getBookmark().getNick();
		} else {
			if (mucParts.length == 2) {
				return mucParts[1];
			} else {
				return account.getUsername();
			}
		}
	}

	public String getActualNick() {
		if (this.self.getName() != null) {
			return this.self.getName();
		} else {
			return this.getProposedNick();
		}
	}

	public void setJoinNick(String nick) {
		this.joinnick = nick;
	}

	public void setConversation(Conversation conversation) {
		this.conversation = conversation;
	}

	public boolean online() {
		return this.isOnline;
	}

	public int getError() {
		return this.error;
	}

	public void setOnRenameListener(OnRenameListener listener) {
		this.renameListener = listener;
	}

	public OnRenameListener getOnRenameListener() {
		return this.renameListener;
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

	public void flagAboutToRename() {
		this.aboutToRename = true;
	}

	public long[] getPgpKeyIds() {
		List<Long> ids = new ArrayList<Long>();
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				ids.add(user.getPgpKeyId());
			}
		}
		long[] primitivLongArray = new long[ids.size()];
		for (int i = 0; i < ids.size(); ++i) {
			primitivLongArray[i] = ids.get(i);
		}
		return primitivLongArray;
	}

	public boolean pgpKeysInUse() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() != 0) {
				return true;
			}
		}
		return false;
	}

	public boolean everybodyHasKeys() {
		for (User user : getUsers()) {
			if (user.getPgpKeyId() == 0) {
				return false;
			}
		}
		return true;
	}

	public String getJoinJid() {
		return this.conversation.getContactJid().split("/",2)[0] + "/"
				+ this.joinnick;
	}

	public String getTrueCounterpart(String counterpart) {
		for (User user : this.getUsers()) {
			if (user.getName().equals(counterpart)) {
				return user.getJid();
			}
		}
		return null;
	}

	public String getPassword() {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().getPassword() != null) {
			return conversation.getBookmark().getPassword();
		} else {
			return this.password;
		}
	}

	public void setPassword(String password) {
		if (conversation.getBookmark() != null
				&& conversation.getBookmark().isProvidePassword()) {
			conversation.getBookmark().setPassword(password);
		} else {
			this.password = password;
		}
	}

	public boolean isPasswordChanged() {
		return this.passwordChanged;
	}

}