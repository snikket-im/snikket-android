package eu.siacs.conversations.xmpp.jingle;

import java.util.Locale;

public enum Media {
    VIDEO, AUDIO, UNKNOWN;

    @Override
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
}
