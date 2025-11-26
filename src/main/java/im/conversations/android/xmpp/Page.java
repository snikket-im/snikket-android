package im.conversations.android.xmpp;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;

public class Page {

    public final String first;
    public final String last;
    public final Integer count;

    public Page(String first, String last, Integer count) {
        this.first = first;
        this.last = last;
        this.count = count;
    }

    public static Page emptyWithCount(final String id, final Integer count) {
        return new Page(id, id, count);
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("first", first)
                .add("last", last)
                .add("count", count)
                .toString();
    }
}
