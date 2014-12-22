package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
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
		return this.getAttributeAsJid("jid");
	}

	@Override
	public List<Tag> getTags() {
		ArrayList<Tag> tags = new ArrayList<Tag>();
		for (Element element : getChildren()) {
			if (element.getName().equals("group") && element.getContent() != null) {
				String group = element.getContent();
				tags.add(new Tag(group, UIHelper.getColorForName(group)));
			}
		}
		return tags;
	}

	public String getNick() {
		Element nick = this.findChild("nick");
		if (nick != null) {
			return nick.getContent();
		} else {
			return null;
		}
	}

	public void setNick(String nick) {
		Element element = this.findChild("nick");
		if (element == null) {
			element = this.addChild("nick");
		}
		element.setContent(nick);
	}

	public boolean autojoin() {
		return this.getAttributeAsBoolean("autojoin");
	}

	public String getPassword() {
		Element password = this.findChild("password");
		if (password != null) {
			return password.getContent();
		} else {
			return null;
		}
	}

	public void setPassword(String password) {
		Element element = this.findChild("password");
		if (element != null) {
			element.setContent(password);
		}
	}

	public boolean match(String needle) {
		if (needle == null) {
			return true;
		}
		needle = needle.toLowerCase(Locale.US);
		final Jid jid = getJid();
		return (jid != null && jid.toString().contains(needle)) ||
			getDisplayName().toLowerCase(Locale.US).contains(needle) ||
			matchInTag(needle);
	}

	private boolean matchInTag(String needle) {
		needle = needle.toLowerCase(Locale.US);
		for (Tag tag : getTags()) {
			if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
				return true;
			}
		}
		return false;
	}

	public Account getAccount() {
		return this.account;
	}

	public Conversation getConversation() {
		return this.mJoinedConversation;
	}

	public void setConversation(Conversation conversation) {
		this.mJoinedConversation = conversation;
	}

	public String getName() {
		return this.getAttribute("name");
	}

	public void setName(String name) {
		this.name = name;
	}

	public void unregisterConversation() {
		if (this.mJoinedConversation != null) {
			this.mJoinedConversation.deregisterWithBookmark();
		}
	}
}
