package eu.siacs.conversations.utils;

import com.google.common.net.InetAddresses;

import java.util.regex.Pattern;

public class IP {

    private static final Pattern PATTERN_IPV4 = Pattern.compile("\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEX4DECCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?) ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_6HEX4DEC = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
    private static final Pattern PATTERN_IPV6_HEXCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");
    private static final Pattern PATTERN_IPV6 = Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");

    public static boolean matches(String server) {
        return server != null && (
                PATTERN_IPV4.matcher(server).matches()
                        || PATTERN_IPV6.matcher(server).matches()
                        || PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
                        || PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
                        || PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches());
    }

    public static String wrapIPv6(final String host) {
        if (matches(host)) {
            return String.format("[%s]", host);
        } else {
            return host;
        }
    }

    public static String unwrapIPv6(final String host) {
        if (host.length() > 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            final String ip = host.substring(1,host.length() -1);
            if (InetAddresses.isInetAddress(ip)) {
                return ip;
            }
        }
        return host;
    }

}
