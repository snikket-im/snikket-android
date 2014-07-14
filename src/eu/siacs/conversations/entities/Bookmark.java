package eu.siacs.conversations.entities;

import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;

public class Bookmark implements ListItem {
	
	private Account account;
	private String jid;
	private String nick;
	private String name;
	private boolean autojoin;
	private Conversation mJoinedConversation;
	
	public Bookmark(Account account) {
		this.account = account;
	}

	public static Bookmark parse(Element element, Account account) {
		Bookmark bookmark = new Bookmark(account);
		bookmark.setJid(element.getAttribute("jid"));
		bookmark.setName(element.getAttribute("name"));
		String autojoin = element.getAttribute("autojoin");
		if (autojoin!=null && (autojoin.equals("true")||autojoin.equals("1"))) {
			bookmark.setAutojoin(true);
		} else {
			bookmark.setAutojoin(false);
		}
		Element nick = element.findChild("nick");
		if (nick!=null) {
			bookmark.setNick(nick.getContent());
		}
		return bookmark;
	}

	public void setAutojoin(boolean autojoin) {
		this.autojoin = autojoin;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setJid(String jid) {
		this.jid = jid;
	}
	
	public void setNick(String nick) {
		this.nick = nick;
	}

	@Override
	public int compareTo(ListItem another) {
		return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
	}

	@Override
	public String getDisplayName() {
		if (this.mJoinedConversation!=null) {
			return this.mJoinedConversation.getName(true);
		} else if (name!=null) {
			return name;
		} else {
			return this.jid.split("@")[0];
		}
	}

	@Override
	public String getJid() {
		return this.jid.toLowerCase(Locale.US);
	}
	
	public String getNick() {
		return this.nick;
	}
	
	public boolean autojoin() {
		return autojoin;
	}

	public boolean match(String needle) {
		return needle == null
				|| getJid().contains(needle.toLowerCase(Locale.US))
				|| getDisplayName().toLowerCase(Locale.US)
						.contains(needle.toLowerCase(Locale.US));
	}

	public Account getAccount() {
		return this.account;
	}

	@Override
	public Bitmap getImage(int dpSize, Context context) {
		if (this.mJoinedConversation==null) {
			return UIHelper.getContactPicture(getDisplayName(), dpSize, context, false);
		} else {
			return UIHelper.getContactPicture(this.mJoinedConversation, dpSize, context, false);
		}
	}

	public void setConversation(Conversation conversation) {
		this.mJoinedConversation = conversation;
	}

	public String getName() {
		return name;
	}
}
