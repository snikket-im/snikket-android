package eu.siacs.conversations.xmpp.manager;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.up.Push;

public class UnifiedPushManager extends AbstractManager {

    private final XmppConnectionService service;

    public UnifiedPushManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.service = service;
    }

    public void push(final Iq packet) {
        final Jid transport = packet.getFrom();
        final var push = packet.getOnlyExtension(Push.class);
        if (push == null || transport == null) {
            connection.sendErrorFor(packet, new Condition.BadRequest());
            return;
        }
        if (service.processUnifiedPushMessage(getAccount(), transport, push)) {
            connection.sendResultFor(packet);
        } else {
            connection.sendErrorFor(packet, new Condition.ItemNotFound());
        }
    }
}
