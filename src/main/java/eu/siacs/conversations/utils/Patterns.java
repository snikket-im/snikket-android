package eu.siacs.conversations.utils;

import java.util.regex.Pattern;

public class Patterns {

    public static final Pattern URI_GENERIC =
            Pattern.compile(
                    "(?<=^|\\s|\\()(tel|xmpp|http|https|geo|mailto|web\\+ap):[\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]+(\\([\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]+\\))*[\\p{L}\\p{M}\\p{N}\\-._~:/?#\\[\\]@!$&'*+,;=%]*");

    public static final Pattern URI_TEL =
            Pattern.compile("^tel:\\+?(\\d{1,4}[-./()\\s]?)*\\d{1,4}(;.*)?$");

    public static final Pattern URI_HTTP = Pattern.compile("https?://\\S+");

    public static final Pattern URI_WEB_AP = Pattern.compile("web\\+ap://\\S+");

    public static Pattern URI_GEO =
            Pattern.compile(
                    "geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)(?:,-?\\d+(?:\\.\\d+)?)?(?:;crs=[\\w-]+)?(?:;u=\\d+(?:\\.\\d+)?)?(?:;[\\w-]+=(?:[\\w-_.!~*'()]|%[\\da-f][\\da-f])+)*(\\?z=\\d+)?",
                    Pattern.CASE_INSENSITIVE);

    public static final Pattern IPV4 =
            Pattern.compile(
                    "\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6 =
            Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");
    public static final Pattern IPV6_HEX4_DECOMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)"
                        + " ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6_6HEX4DEC =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    public static final Pattern IPV6_HEX_COMPRESSED =
            Pattern.compile(
                    "\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");

    private Patterns() {
        throw new AssertionError("Do not instantiate me");
    }
}
