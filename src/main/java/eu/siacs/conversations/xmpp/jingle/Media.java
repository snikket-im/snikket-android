package eu.siacs.conversations.xmpp.jingle;

import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableSet;
import java.util.Locale;
import java.util.Set;

public enum Media {
    VIDEO,
    AUDIO,
    UNKNOWN;

    @Override
    @NonNull
    public String toString() {
        return super.toString().toLowerCase(Locale.ROOT);
    }

    public static Media of(String value) {
        try {
            return value == null ? UNKNOWN : Media.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public static boolean audioOnly(Set<Media> media) {
        return ImmutableSet.of(AUDIO).equals(media);
    }

    public static boolean videoOnly(Set<Media> media) {
        return ImmutableSet.of(VIDEO).equals(media);
    }
}
