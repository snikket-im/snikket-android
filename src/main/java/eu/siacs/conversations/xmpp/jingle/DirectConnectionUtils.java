package eu.siacs.conversations.xmpp.jingle;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import rocks.xmpp.addr.Jid;

public class DirectConnectionUtils {

    private static List<InetAddress> getLocalAddresses() {
        final List<InetAddress> addresses = new ArrayList<>();
        final Enumeration<NetworkInterface> interfaces;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return addresses;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            final Enumeration<InetAddress> inetAddressEnumeration = networkInterface.getInetAddresses();
            while (inetAddressEnumeration.hasMoreElements()) {
                final InetAddress inetAddress = inetAddressEnumeration.nextElement();
                if (!inetAddress.isLoopbackAddress()) {
                    addresses.add(inetAddress);
                }
            }
        }
        return addresses;
    }

    public static List<JingleCandidate> getLocalCandidates(Jid jid) {
        SecureRandom random = new SecureRandom();
        ArrayList<JingleCandidate> candidates = new ArrayList<>();
        for (InetAddress inetAddress : getLocalAddresses()) {
            final JingleCandidate candidate = new JingleCandidate(UUID.randomUUID().toString(), true);
            candidate.setHost(inetAddress.getHostAddress());
            candidate.setPort(random.nextInt(60000) + 1024);
            candidate.setType(JingleCandidate.TYPE_DIRECT);
            candidate.setJid(jid);
            candidate.setPriority(8257536 + candidates.size());
            candidates.add(candidate);
        }
        return candidates;
    }

}
