package eu.siacs.conversations.entities;

import java.util.List;

import eu.siacs.conversations.xmpp.jid.Jid;

public interface ListItem extends Comparable<ListItem> {
	public String getDisplayName();

	public Jid getJid();

	public List<Tag> getTags();

	public final class Tag {
		private final String name;
		private final int color;

		public Tag(final String name, final int color) {
			this.name = name;
			this.color = color;
		}

		public int getColor() {
			return this.color;
		}

		public String getName() {
			return this.name;
		}
	}

	public boolean match(final String needle);
}
