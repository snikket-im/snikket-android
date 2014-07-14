package eu.siacs.conversations.entities;

import java.util.Locale;

import eu.siacs.conversations.xml.Element;

public class Bookmark implements ListItem {
	
	private Account account;
	private String jid;
	private String nick;
	private String displayName;
	private boolean autojoin;
	
	public Bookmark(Account account) {
		this.account = account;
	}

	public static Bookmark parse(Element element, Account account) {
		Bookmark bookmark = new Bookmark(account);
		bookmark.setJid(element.getAttribute("jid"));
		bookmark.setDisplayName(element.getAttribute("name"));
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
	
	public void setDisplayName(String name) {
		this.displayName = name;
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
		if (displayName!=null) {
			return displayName;
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

	@Override
	public String getProfilePhoto() {
		return null;
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
}
