package eu.siacs.conversations.entities;

import android.content.Context;

import java.util.List;

import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;


public interface ListItem extends Comparable<ListItem>, AvatarService.Avatarable {
	String getDisplayName();

	Jid getJid();

	List<Tag> getTags(Context context);

	final class Tag {
		private final String name;

		public Tag(final String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}
	}

	boolean match(Context context, final String needle);
}
