package eu.siacs.conversations.utils;

import java.util.regex.Pattern;

public class Patterns {

    public static final Pattern URI_GENERIC =
            Pattern.compile(
                    "(?<=^|\\s)(tel|xmpp|http|https|geo|mailto):[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    public static final Pattern URI_TEL =
            Pattern.compile("^tel:\\+?(\\d{1,4}[-./()\\s]?)*\\d{1,4}(;.*)?$");

    public static final Pattern URI_HTTP = Pattern.compile("https?://\\S+");

    public static Pattern URI_GEO =
            Pattern.compile(
                    "geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,-?\\d+(?:\\.\\d+)?)?(?:;crs=[\\w-]+)?(?:;u=\\d+(?:\\.\\d+)?)?(?:;[\\w-]+=(?:[\\w-_.!~*'()]|%[\\da-f][\\da-f])+)*(\\?z=\\d+)?",
                    Pattern.CASE_INSENSITIVE);

    private Patterns() {
        throw new AssertionError("Do not instantiate me");
    }
}
