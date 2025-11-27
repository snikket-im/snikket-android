package eu.siacs.conversations.xmpp.jingle;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.disco.info.InfoQuery;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public class RtpCapability {

    private static final List<String> BASIC_RTP_REQUIREMENTS =
            Arrays.asList(
                    Namespace.JINGLE,
                    Namespace.JINGLE_TRANSPORT_ICE_UDP,
                    Namespace.JINGLE_APPS_RTP,
                    Namespace.JINGLE_APPS_DTLS);
    private static final Collection<String> VIDEO_REQUIREMENTS =
            Arrays.asList(Namespace.JINGLE_FEATURE_AUDIO, Namespace.JINGLE_FEATURE_VIDEO);

    public static Capability check(@Nullable final InfoQuery infoQuery) {
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

    public static List<Presence> filterPresences(final Contact contact, Capability required) {
        final var connection = contact.getAccount().getXmppConnection();

        final var builder = new ImmutableList.Builder<Presence>();

        for (final var presence : contact.getPresences()) {
            final Capability capability =
                    check(connection.getManager(DiscoManager.class).get(presence.getFrom()));
            if (capability == Capability.NONE) {
                continue;
            }
            if (required == Capability.AUDIO || capability == required) {
                builder.add(presence);
            }
        }
        return builder.build();
    }

    public static Capability checkWithFallback(final Contact contact) {
        final var presences = contact.getPresences();
        if (presences.isEmpty() && contact.getAccount().isEnabled()) {
            return contact.getRtpCapability();
        }
        return check(contact, presences);
    }

    public static Capability check(final Contact contact, final List<Presence> presences) {
        final var connection = contact.getAccount().getXmppConnection();
        if (connection == null) {
            return Capability.NONE;
        }
        Set<Capability> capabilities =
                ImmutableSet.copyOf(
                        Collections2.transform(
                                presences,
                                p ->
                                        check(
                                                connection
                                                        .getManager(DiscoManager.class)
                                                        .get(p.getFrom()))));
        if (capabilities.contains(Capability.VIDEO)) {
            return Capability.VIDEO;
        } else if (capabilities.contains(Capability.AUDIO)) {
            return Capability.AUDIO;
        } else {
            return Capability.NONE;
        }
    }

    // do all devices that support Rtp Call also support JMI?
    public static boolean jmiSupport(final Contact contact) {
        final var connection = contact.getAccount().getXmppConnection();
        if (connection == null) {
            return false;
        }
        return !Collections2.transform(
                        Collections2.filter(
                                contact.getFullAddresses(),
                                a ->
                                        RtpCapability.check(
                                                        connection
                                                                .getManager(DiscoManager.class)
                                                                .get(a))
                                                != Capability.NONE),
                        a -> {
                            final var disco = connection.getManager(DiscoManager.class).get(a);
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
