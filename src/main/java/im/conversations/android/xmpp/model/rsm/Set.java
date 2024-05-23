package im.conversations.android.xmpp.model.rsm;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.Page;
import im.conversations.android.xmpp.Range;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Set extends Extension {

    public Set() {
        super(Set.class);
    }

    public static Set of(final Range range, final Integer max) {
        final var set = new Set();
        if (range.order == Range.Order.NORMAL) {
            final var after = set.addExtension(new After());
            after.setContent(range.id);
        } else if (range.order == Range.Order.REVERSE) {
            final var before = set.addExtension(new Before());
            before.setContent(range.id);
        } else {
            throw new IllegalArgumentException("Invalid order");
        }
        if (max != null) {
            set.addExtension(new Max()).setMax(max);
        }
        return set;
    }

    public Page asPage() {
        final var first = this.getExtension(First.class);
        final var last = this.getExtension(Last.class);

        final var firstId = first == null ? null : first.getContent();
        final var lastId = last == null ? null : last.getContent();
        if (Strings.isNullOrEmpty(firstId) || Strings.isNullOrEmpty(lastId)) {
            throw new IllegalStateException("Invalid page. Missing first or last");
        }
        return new Page(firstId, lastId, this.getCount());
    }

    public boolean isEmpty() {
        final var first = this.getExtension(First.class);
        final var last = this.getExtension(Last.class);
        return first == null && last == null;
    }

    public Integer getCount() {
        final var count = this.getExtension(Count.class);
        return count == null ? null : count.getCount();
    }
}
