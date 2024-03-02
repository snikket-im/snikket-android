package eu.siacs.conversations.xmpp.jingle;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractContentMap<
        D extends GenericDescription, T extends GenericTransportInfo> {

    public final Group group;

    public final Map<String, DescriptionTransport<D, T>> contents;

    protected AbstractContentMap(
            final Group group, final Map<String, DescriptionTransport<D, T>> contents) {
        this.group = group;
        this.contents = contents;
    }

    public static class UnsupportedApplicationException extends IllegalArgumentException {
        UnsupportedApplicationException(String message) {
            super(message);
        }
    }

    public static class UnsupportedTransportException extends IllegalArgumentException {
        UnsupportedTransportException(String message) {
            super(message);
        }
    }

    public Set<Content.Senders> getSenders() {
        return ImmutableSet.copyOf(Collections2.transform(contents.values(), dt -> dt.senders));
    }

    public List<String> getNames() {
        return ImmutableList.copyOf(contents.keySet());
    }

    JinglePacket toJinglePacket(final JinglePacket.Action action, final String sessionId) {
        final JinglePacket jinglePacket = new JinglePacket(action, sessionId);
        for (final Map.Entry<String, DescriptionTransport<D, T>> entry : this.contents.entrySet()) {
            final DescriptionTransport<D, T> descriptionTransport = entry.getValue();
            final Content content =
                    new Content(
                            Content.Creator.INITIATOR,
                            descriptionTransport.senders,
                            entry.getKey());
            if (descriptionTransport.description != null) {
                content.addChild(descriptionTransport.description);
            }
            content.addChild(descriptionTransport.transport);
            jinglePacket.addJingleContent(content);
        }
        if (this.group != null) {
            jinglePacket.addGroup(this.group);
        }
        return jinglePacket;
    }

    void requireContentDescriptions() {
        if (this.contents.size() == 0) {
            throw new IllegalStateException("No contents available");
        }
        for (final Map.Entry<String, DescriptionTransport<D, T>> entry : this.contents.entrySet()) {
            if (entry.getValue().description == null) {
                throw new IllegalStateException(
                        String.format("%s is lacking content description", entry.getKey()));
            }
        }
    }
}
