package im.conversations.android.xmpp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Timestamps {

    private Timestamps() {
        throw new IllegalStateException("Do not instantiate me");
    }

    public static long parse(final String input) throws ParseException {
        if (input == null) {
            throw new IllegalArgumentException("timestamp should not be null");
        }
        final String timestamp = input.replace("Z", "+0000");
        final SimpleDateFormat simpleDateFormat =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        final long milliseconds = getMilliseconds(timestamp);
        final String formatted =
                timestamp.substring(0, 19) + timestamp.substring(timestamp.length() - 5);
        final Date date = simpleDateFormat.parse(formatted);
        if (date == null) {
            throw new IllegalArgumentException("Date was null");
        }
        return date.getTime() + milliseconds;
    }

    private static long getMilliseconds(final String timestamp) {
        if (timestamp.length() >= 25 && timestamp.charAt(19) == '.') {
            final String millis = timestamp.substring(19, timestamp.length() - 5);
            try {
                double fractions = Double.parseDouble("0" + millis);
                return Math.round(1000 * fractions);
            } catch (final NumberFormatException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }
}
