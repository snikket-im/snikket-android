package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.entities.MucOptions.User;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.PresencePacket;
import android.annotation.SuppressLint;
import android.util.Log;

@SuppressLint("DefaultLocale")
public class MucOptions {
	public static final int ERROR_NICK_IN_USE = 1;
	
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
		
		public String getName() {
			return name;
		}
		public void setName(String user) {
			this.name = user;
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
	}
	private ArrayList<User> users = new ArrayList<User>();
	private Conversation conversation;
	private boolean isOnline = false;
	private int error = 0;
	private OnRenameListener renameListener = null;
	private User self = new User();

	
	public void deleteUser(String name) {
		for(int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(name)) {
				users.remove(i);
				return;
			}
		}
	}
	
	public void addUser(User user) {
		for(int i = 0; i < users.size(); ++i) {
			if (users.get(i).getName().equals(user.getName())) {
				users.set(i, user);
				return;
			}
		}
		users.add(user);
		}
	
	public void processPacket(PresencePacket packet) {
		Log.d("xmppService","process Packet for muc options: "+packet.toString());
		String name = packet.getAttribute("from").split("/")[1];
			String type = packet.getAttribute("type");
			if (type==null) {
				User user = new User();
				Element item = packet.findChild("x").findChild("item");
				user.setName(name);
				user.setAffiliation(item.getAttribute("affiliation"));
				user.setRole(item.getAttribute("role"));
				user.setName(name);
				Log.d("xmppService","nick: "+getNick());
				Log.d("xmppService","name: "+name);
				if (name.equals(getNick())) {
					this.isOnline = true;
					this.error = 0;
					self = user;
				} else {
					addUser(user);
				}
			} else if (type.equals("unavailable")) {
				Log.d("xmppService","name: "+name);
				if (name.equals(getNick())) {
					Element item = packet.findChild("x").findChild("item");
					Log.d("xmppService","nick equals name");
					String nick = item.getAttribute("nick");
					if (nick!=null) {
						if (renameListener!=null) {
							renameListener.onRename(true);
						}
						this.setNick(nick);
					}
				}
				deleteUser(packet.getAttribute("from").split("/")[1]);
			} else if (type.equals("error")) {
				Element error = packet.findChild("error");
				if (error.hasChild("conflict")) {
					this.error  = ERROR_NICK_IN_USE;
				}
			}
	}
	
	public List<User> getUsers() {
		return this.users;
	}
	
	public String getNick() {
		String[] split = conversation.getContactJid().split("/");
		if (split.length == 2) {
			return split[1];
		} else {
			return conversation.getAccount().getUsername();
		}
	}
	
	public void setNick(String nick) {
		String jid = conversation.getContactJid().split("/")[0]+"/"+nick;
		conversation.setContactJid(jid);
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
}