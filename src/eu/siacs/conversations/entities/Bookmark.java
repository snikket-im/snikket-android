package eu.siacs.conversations.entities;

import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;

public class Bookmark extends Element implements ListItem {

	private Account account;
	private Conversation mJoinedConversation;

	public Bookmark(Account account, String jid) {
		super("conference");
		this.setAttribute("jid", jid);
		this.account = account;
	}

	private Bookmark(Account account) {
		super("conference");
		this.account = account;
	}

	public static Bookmark parse(Element element, Account account) {
		Bookmark bookmark = new Bookmark(account);
		bookmark.setAttributes(element.getAttributes());
		bookmark.setChildren(element.getChildren());
		return bookmark;
	}

	public void setAutojoin(boolean autojoin) {
		if (autojoin) {
			this.setAttribute("autojoin", "true");
		} else {
			this.setAttribute("autojoin", "false");
		}
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setNick(String nick) {
		Element element = this.findChild("nick");
		if (element == null) {
			element = this.addChild("nick");
		}
		element.setContent(nick);
	}

	public void setPassword(String password) {
		Element element = this.findChild("password");
		if (element != null) {
			element.setContent(password);
		}
	}

	@Override
	public int compareTo(ListItem another) {
		return this.getDisplayName().compareToIgnoreCase(
				another.getDisplayName());
	}

	@Override
	public String getDisplayName() {
		if (this.mJoinedConversation != null
				&& (this.mJoinedConversation.getMucOptions().getSubject() != null)) {
			return this.mJoinedConversation.getMucOptions().getSubject();
		} else if (getName() != null) {
			return getName();
		} else {
			return this.getJid().split("@")[0];
		}
	}

	@Override
	public String getJid() {
		String jid = this.getAttribute("jid");
		if (jid != null) {
			return jid.toLowerCase(Locale.US);
		} else {
			return null;
		}
	}

	public String getNick() {
		Element nick = this.findChild("nick");
		if (nick != null) {
			return nick.getContent();
		} else {
			return null;
		}
	}

	public boolean autojoin() {
		String autojoin = this.getAttribute("autojoin");
		return (autojoin != null && (autojoin.equalsIgnoreCase("true") || autojoin
				.equalsIgnoreCase("1")));
	}

	public String getPassword() {
		Element password = this.findChild("password");
		if (password != null) {
			return password.getContent();
		} else {
			return null;
		}
	}

	public boolean match(String needle) {
		return needle == null
				|| getJid().contains(needle.toLowerCase(Locale.US))
				|| getDisplayName().toLowerCase(Locale.US).contains(
						needle.toLowerCase(Locale.US));
	}

	public Account getAccount() {
		return this.account;
	}

	@Override
	public Bitmap getImage(int dpSize, Context context) {
		if (this.mJoinedConversation == null) {
			return UIHelper.getContactPicture(getDisplayName(), dpSize,
					context, false);
		} else {
			return UIHelper.getContactPicture(this.mJoinedConversation, dpSize,
					context, false);
		}
	}

	public void setConversation(Conversation conversation) {
		this.mJoinedConversation = conversation;
	}

	public String getName() {
		return this.getAttribute("name");
	}

	public void unregisterConversation() {
		if (this.mJoinedConversation != null) {
			this.mJoinedConversation.deregisterWithBookmark();
		}
	}
}
