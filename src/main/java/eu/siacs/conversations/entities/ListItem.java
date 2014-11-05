package eu.siacs.conversations.entities;

import eu.siacs.conversations.xmpp.jid.Jid;

public interface ListItem extends Comparable<ListItem> {
	public String getDisplayName();

	public Jid getJid();
}
