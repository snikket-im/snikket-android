package eu.siacs.conversations.xmpp.jingle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.xml.Namespace;

public class RtpCapability {

    private static List<String> BASIC_RTP_REQUIREMENTS = Arrays.asList(
            Namespace.JINGLE,
            Namespace.JINGLE_TRANSPORT_ICE_UDP,
            Namespace.JINGLE_APPS_RTP,
            Namespace.JINGLE_APPS_DTLS
    );
    private static List<String> VIDEO_REQUIREMENTS = Arrays.asList(
            Namespace.JINGLE_FEATURE_AUDIO,
            Namespace.JINGLE_FEATURE_VIDEO
    );

    public static Capability check(final Presence presence) {
        final ServiceDiscoveryResult disco = presence.getServiceDiscoveryResult();
        final List<String> features = disco == null ? Collections.emptyList() : disco.getFeatures();
        if (features.containsAll(BASIC_RTP_REQUIREMENTS)) {
            if (features.containsAll(VIDEO_REQUIREMENTS)) {
                return Capability.VIDEO;
            }
            if (features.contains(Namespace.JINGLE_FEATURE_AUDIO)) {
                return Capability.AUDIO;
            }
        }
        return Capability.NONE;
    }

    public static Capability check(final Contact contact) {
        final Presences presences = contact.getPresences();
        Capability result = Capability.NONE;
        for(Presence presence : presences.getPresences().values()) {
            Capability capability = check(presence);
            if (capability == Capability.VIDEO) {
                result = capability;
            } else if (capability == Capability.AUDIO && result == Capability.NONE) {
                result = capability;
            }
        }
        return result;
    }

    public enum Capability {
        NONE, AUDIO, VIDEO
    }

}
