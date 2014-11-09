package eu.siacs.conversations.entities;

import java.util.Locale;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Bookmark extends Element implements ListItem {

	private Account account;
	private Conversation mJoinedConversation;

	public Bookmark(final Account account, final Jid jid) {
		super("conference");
		this.setAttribute("jid", jid.toString());
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
	public int compareTo(final ListItem another) {
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
			return this.getJid().getLocalpart();
		}
	}

	@Override
	public Jid getJid() {
		final String jid = this.getAttribute("jid");
		if (jid != null) {
            try {
                return Jid.fromString(jid);
            } catch (final InvalidJidException e) {
                return null;
            }
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
				|| getJid().toString().toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US))
				|| getDisplayName().toLowerCase(Locale.US).contains(
						needle.toLowerCase(Locale.US));
	}

	public Account getAccount() {
		return this.account;
	}

	public void setConversation(Conversation conversation) {
		this.mJoinedConversation = conversation;
	}

	public Conversation getConversation() {
		return this.mJoinedConversation;
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
