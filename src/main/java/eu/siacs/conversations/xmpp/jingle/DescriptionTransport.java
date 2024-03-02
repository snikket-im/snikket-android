package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;

public class DescriptionTransport<D extends GenericDescription, T extends GenericTransportInfo> {

    public final Content.Senders senders;
    public final D description;
    public final T transport;

    public DescriptionTransport(
            final Content.Senders senders, final D description, final T transport) {
        this.senders = senders;
        this.description = description;
        this.transport = transport;
    }
}
