package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RtpCapability {

    private static final List<String> BASIC_RTP_REQUIREMENTS =
            Arrays.asList(
                    Namespace.JINGLE,
                    Namespace.JINGLE_TRANSPORT_ICE_UDP,
                    Namespace.JINGLE_APPS_RTP,
                    Namespace.JINGLE_APPS_DTLS);
    private static final Collection<String> VIDEO_REQUIREMENTS =
            Arrays.asList(Namespace.JINGLE_FEATURE_AUDIO, Namespace.JINGLE_FEATURE_VIDEO);

    public static Capability check(final InfoQuery infoQuery) {
        final Set<String> features =
                infoQuery == null
                        ? Collections.emptySet()
                        : ImmutableSet.copyOf(infoQuery.getFeatureStrings());
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

    public static String[] filterPresences(final Contact contact, Capability required) {
        final var connection = contact.getAccount().getXmppConnection();
        if (connection == null) {
            return new String[0];
        }
        final Presences presences = contact.getPresences();
        final ArrayList<String> resources = new ArrayList<>();
        for (final String resource : presences.getPresencesMap().keySet()) {
            final var jid =
                    Strings.isNullOrEmpty(resource)
                            ? contact.getAddress().asBareJid()
                            : contact.getAddress().withResource(resource);
            final Capability capability = check(connection.getManager(DiscoManager.class).get(jid));
            if (capability == Capability.NONE) {
                continue;
            }
            if (required == Capability.AUDIO || capability == required) {
                resources.add(resource);
            }
        }
        return resources.toArray(new String[0]);
    }

    public static Capability check(final Contact contact) {
        return check(contact, true);
    }

    public static Capability check(final Contact contact, final boolean allowFallback) {
        final Presences presences = contact.getPresences();
        if (presences.isEmpty() && allowFallback && contact.getAccount().isEnabled()) {
            return contact.getRtpCapability();
        }
        final var connection = contact.getAccount().getXmppConnection();
        if (connection == null) {
            return Capability.NONE;
        }
        Capability result = Capability.NONE;
        for (final String resource : presences.getPresencesMap().keySet()) {
            final var jid =
                    Strings.isNullOrEmpty(resource)
                            ? contact.getAddress().asBareJid()
                            : contact.getAddress().withResource(resource);
            final Capability capability = check(connection.getManager(DiscoManager.class).get(jid));
            if (capability == Capability.VIDEO) {
                result = capability;
            } else if (capability == Capability.AUDIO && result == Capability.NONE) {
                result = capability;
            }
        }
        return result;
    }

    // do all devices that support Rtp Call also support JMI?
    public static boolean jmiSupport(final Contact contact) {
        final var connection = contact.getAccount().getXmppConnection();
        if (connection == null) {
            return false;
        }
        return !Collections2.transform(
                        Collections2.filter(
                                contact.getPresences().getPresencesMap().keySet(),
                                p ->
                                        RtpCapability.check(
                                                        connection
                                                                .getManager(DiscoManager.class)
                                                                .get(
                                                                        Strings.isNullOrEmpty(p)
                                                                                ? contact.getAddress()
                                                                                        .asBareJid()
                                                                                : contact.getAddress()
                                                                                        .withResource(
                                                                                                p)))
                                                != Capability.NONE),
                        p -> {
                            final var disco =
                                    connection
                                            .getManager(DiscoManager.class)
                                            .get(
                                                    Strings.isNullOrEmpty(p)
                                                            ? contact.getAddress().asBareJid()
                                                            : contact.getAddress().withResource(p));
                            return disco != null
                                    && disco.getFeatureStrings().contains(Namespace.JINGLE_MESSAGE);
                        })
                .contains(false);
    }

    public enum Capability {
        NONE,
        AUDIO,
        VIDEO;

        public static Capability of(String value) {
            if (Strings.isNullOrEmpty(value)) {
                return NONE;
            }
            try {
                return valueOf(value);
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }
}
