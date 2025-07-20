package eu.siacs.conversations.entities;

import android.util.Pair;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Presences {

    private Presences() {
        throw new IllegalStateException("Do not instantiate me");
    }

    private static String nameWithoutVersion(final String name) {
        String[] parts = name.split(" ");
        if (parts.length > 1 && Character.isDigit(parts[parts.length - 1].charAt(0))) {
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < parts.length - 1; ++i) {
                if (output.length() != 0) {
                    output.append(' ');
                }
                output.append(parts[i]);
            }
            return output.toString();
        } else {
            return name;
        }
    }

    public static Collection<PresenceTemplate> asTemplates(final List<Presence> presences) {
        return Collections2.transform(
                Collections2.filter(presences, p -> !Strings.isNullOrEmpty(p.getStatus())),
                p -> new PresenceTemplate(p.getAvailability(), p.getStatus()));
    }

    public static Pair<Map<Jid, String>, Map<Jid, String>> toTypeAndNameMap(
            final Account account, final List<Presence> presences) {
        final var connection = account.getXmppConnection();
        Map<Jid, String> typeMap = new HashMap<>();
        Map<Jid, String> nameMap = new HashMap<>();
        for (final var presence : presences) {
            final var serviceDiscoveryResult =
                    connection.getManager(DiscoManager.class).get(presence.getFrom());
            if (serviceDiscoveryResult != null
                    && !serviceDiscoveryResult.getIdentities().isEmpty()) {
                final Identity identity =
                        Iterables.getFirst(serviceDiscoveryResult.getIdentities(), null);
                String type = identity.getType();
                String name = identity.getIdentityName();
                if (type != null) {
                    typeMap.put(presence.getFrom(), type);
                }
                if (name != null) {
                    nameMap.put(presence.getFrom(), nameWithoutVersion(name));
                }
            }
        }
        return new Pair<>(typeMap, nameMap);
    }
}
