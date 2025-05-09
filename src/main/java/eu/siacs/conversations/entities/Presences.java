package eu.siacs.conversations.entities;

import android.util.Pair;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.xmpp.manager.DiscoManager;
import im.conversations.android.xmpp.model.disco.info.Identity;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Presences {
    private final HashMap<String, Presence> presences = new HashMap<>();
    private final Contact contact;

    public Presences(final Contact contact) {
        this.contact = contact;
    }

    private static String nameWithoutVersion(String name) {
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

    public List<Presence> getPresences() {
        synchronized (this.presences) {
            return new ArrayList<>(this.presences.values());
        }
    }

    public Map<String, Presence> getPresencesMap() {
        synchronized (this.presences) {
            return new HashMap<>(this.presences);
        }
    }

    public Presence get(String resource) {
        synchronized (this.presences) {
            return this.presences.get(resource);
        }
    }

    public void updatePresence(String resource, Presence presence) {
        synchronized (this.presences) {
            this.presences.put(resource, presence);
        }
    }

    public void removePresence(String resource) {
        synchronized (this.presences) {
            this.presences.remove(resource);
        }
    }

    public void clearPresences() {
        synchronized (this.presences) {
            this.presences.clear();
        }
    }

    public Presence.Availability getShownStatus() {
        Presence.Availability highestAvailability = Presence.Availability.OFFLINE;
        synchronized (this.presences) {
            for (final Presence p : presences.values()) {
                final var availability = p.getAvailability();
                if (availability == Presence.Availability.DND) {
                    return availability;
                } else if (availability.compareTo(highestAvailability) < 0) {
                    highestAvailability = availability;
                }
            }
        }
        return highestAvailability;
    }

    public int size() {
        synchronized (this.presences) {
            return presences.size();
        }
    }

    public boolean isEmpty() {
        synchronized (this.presences) {
            return this.presences.isEmpty();
        }
    }

    public String[] toResourceArray() {
        synchronized (this.presences) {
            final String[] presencesArray = new String[presences.size()];
            presences.keySet().toArray(presencesArray);
            return presencesArray;
        }
    }

    public List<PresenceTemplate> asTemplates() {
        synchronized (this.presences) {
            ArrayList<PresenceTemplate> templates = new ArrayList<>(presences.size());
            for (Presence presence : this.presences.values()) {
                String message = Strings.nullToEmpty(presence.getStatus()).trim();
                if (Strings.isNullOrEmpty(message)) {
                    continue;
                }
                templates.add(new PresenceTemplate(presence.getAvailability(), message));
            }
            return templates;
        }
    }

    public boolean has(String presence) {
        synchronized (this.presences) {
            return presences.containsKey(presence);
        }
    }

    public Set<String> getStatusMessages() {
        Set<String> messages = new HashSet<>();
        synchronized (this.presences) {
            for (Presence presence : this.presences.values()) {
                String message = Strings.nullToEmpty(presence.getStatus()).trim();
                if (Strings.isNullOrEmpty(message)) {
                    continue;
                }
                messages.add(message);
            }
        }
        return messages;
    }

    public boolean allOrNonSupport(String namespace) {
        final var connection = this.contact.getAccount().getXmppConnection();
        if (connection == null) {
            return true;
        }
        synchronized (this.presences) {
            for (var resource : this.presences.keySet()) {
                final var disco =
                        connection
                                .getManager(DiscoManager.class)
                                .get(
                                        Strings.isNullOrEmpty(resource)
                                                ? contact.getJid().asBareJid()
                                                : contact.getJid().withResource(resource));
                if (disco == null || !disco.getFeatureStrings().contains(namespace)) {
                    return false;
                }
            }
        }
        return true;
    }

    public Pair<Map<String, String>, Map<String, String>> toTypeAndNameMap() {
        Map<String, String> typeMap = new HashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        final var connection = this.contact.getAccount().getXmppConnection();
        if (connection == null) {
            return new Pair<>(typeMap, nameMap);
        }
        synchronized (this.presences) {
            for (final String resource : this.presences.keySet()) {
                final var serviceDiscoveryResult =
                        connection
                                .getManager(DiscoManager.class)
                                .get(
                                        Strings.isNullOrEmpty(resource)
                                                ? contact.getJid().asBareJid()
                                                : contact.getJid().withResource(resource));
                if (serviceDiscoveryResult != null
                        && !serviceDiscoveryResult.getIdentities().isEmpty()) {
                    final Identity identity =
                            Iterables.getFirst(serviceDiscoveryResult.getIdentities(), null);
                    String type = identity.getType();
                    String name = identity.getIdentityName();
                    if (type != null) {
                        typeMap.put(resource, type);
                    }
                    if (name != null) {
                        nameMap.put(resource, nameWithoutVersion(name));
                    }
                }
            }
        }
        return new Pair<>(typeMap, nameMap);
    }
}
