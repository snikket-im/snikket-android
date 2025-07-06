package eu.siacs.conversations.entities;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.xmpp.Jid;
import java.util.Collection;
import java.util.Locale;

public interface ListItem extends Comparable<ListItem>, AvatarService.Avatar {
    String getDisplayName();

    Jid getAddress();

    Collection<Tag> getTags();

    default boolean match(final String needle) {
        if (Strings.isNullOrEmpty(needle)) {
            return true;
        }
        final var parts =
                Splitter.on(CharMatcher.whitespace())
                        .omitEmptyStrings()
                        .trimResults()
                        .splitToList(needle.toLowerCase(Locale.ROOT));
        if (parts.size() == 1) {
            return matchInItem(Iterables.getOnlyElement(parts));
        } else {
            for (final var part : parts) {
                if (!matchInItem(part)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    default int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    private boolean matchInItem(final String needle) {
        return getAddress().toString().contains(needle)
                || getDisplayName().toLowerCase(Locale.US).contains(needle)
                || matchInTag(needle);
    }

    private boolean matchInTag(final String needle) {
        for (final Tag tag : getTags()) {
            if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    final class Tag {
        private final String name;

        private Tag(final String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public static Collection<Tag> of(final Collection<String> tags) {
            return Collections2.transform(tags, Tag::new);
        }
    }
}
