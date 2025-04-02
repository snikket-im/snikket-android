package eu.siacs.conversations.utils;

import com.google.common.net.InetAddresses;
import de.gultsch.common.Patterns;
import java.net.InetAddress;

public class IP {

    public static boolean matches(final String server) {
        return server != null
                && (Patterns.IPV4.matcher(server).matches()
                        || Patterns.IPV6.matcher(server).matches()
                        || Patterns.IPV6_6HEX4DEC.matcher(server).matches()
                        || Patterns.IPV6_HEX4_DECOMPRESSED.matcher(server).matches()
                        || Patterns.IPV6_HEX_COMPRESSED.matcher(server).matches());
    }

    public static String wrapIPv6(final String host) {
        if (InetAddresses.isInetAddress(host)) {
            final InetAddress inetAddress;
            try {
                inetAddress = InetAddresses.forString(host);
            } catch (final IllegalArgumentException e) {
                return host;
            }
            return InetAddresses.toUriString(inetAddress);
        } else {
            return host;
        }
    }

    public static String unwrapIPv6(final String host) {
        if (host.length() > 2 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            final String ip = host.substring(1, host.length() - 1);
            if (InetAddresses.isInetAddress(ip)) {
                return ip;
            }
        }
        return host;
    }
}
