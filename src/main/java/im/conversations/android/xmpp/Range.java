package im.conversations.android.xmpp;

import androidx.annotation.NonNull;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class Range {

    public final Order order;
    public final String id;

    public Range(final Order order, final String id) {
        this.order = order;
        this.id = id;
    }

    @NonNull
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("order", order).add("id", id).toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return order == range.order && Objects.equal(id, range.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(order, id);
    }

    public enum Order {
        NORMAL,
        REVERSE
    }
}
