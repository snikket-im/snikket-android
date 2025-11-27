package im.conversations.android.xmpp.model.idle;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Ordering;
import java.time.Instant;
import java.util.Collection;
import org.jspecify.annotations.NonNull;

public abstract class LastUserInteraction {

    private static final Ordering<LastUserInteraction> ORDERING =
            new Ordering<>() {
                @Override
                public int compare(LastUserInteraction left, LastUserInteraction right) {
                    return toInstant(left).compareTo(toInstant(right));
                }

                private static Instant toInstant(final LastUserInteraction interaction) {
                    if (interaction instanceof Online) {
                        return Instant.MAX;
                    }
                    if (interaction instanceof None) {
                        return Instant.MIN;
                    }
                    if (interaction instanceof Idle idle) {
                        return idle.since;
                    }
                    throw new AssertionError("Can not convert to instant");
                }
            };

    public static LastUserInteraction max(final Collection<LastUserInteraction> interactions) {
        if (interactions.isEmpty()) {
            return none();
        }
        return ORDERING.max(interactions);
    }

    public static None none() {
        return new None();
    }

    public static Online online() {
        return new Online();
    }

    public static Idle idle(final Instant since) {
        return new Idle(since);
    }

    public static final class None extends LastUserInteraction {
        private None() {}

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this).toString();
        }
    }

    public static final class Online extends LastUserInteraction {
        private Online() {}

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this).toString();
        }
    }

    public static final class Idle extends LastUserInteraction {
        private final Instant since;

        public Idle(final Instant since) {
            this.since = since;
        }

        public Instant getSince() {
            return this.since;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this).add("since", since).toString();
        }
    }
}
