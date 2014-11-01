package eu.siacs.conversations.entities;

public interface ListItem extends Comparable<ListItem> {
	public String getDisplayName();

	public String getJid();
}
