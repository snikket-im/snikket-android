package eu.siacs.conversations.xmpp.manager;

import eu.siacs.conversations.entities.ReceiptRequest;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import im.conversations.android.xmpp.model.hints.Store;
import im.conversations.android.xmpp.model.receipts.Received;
import im.conversations.android.xmpp.model.receipts.Request;
import im.conversations.android.xmpp.model.stanza.Message;

public class DeliveryReceiptManager extends AbstractManager {

    private final XmppConnectionService service;

    public DeliveryReceiptManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void processReceived(final Message message, final MessageArchiveManager.Query query) {
        final var received = message.getExtension(Received.class);
        final var to = message.getTo();
        final var from = message.getFrom();
        final var account = this.getAccount();
        final var id = received.getId();
        if (message.fromAccount(account)) {
            if (query != null && id != null && to != null) {
                query.removePendingReceiptRequest(new ReceiptRequest(to, id));
            }
        }

        if (from == null || id == null) {
            return;
        }

        if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
            final String sessionId =
                    id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
            this.service
                    .getJingleConnectionManager()
                    .updateProposedSessionDiscovered(
                            account,
                            from,
                            sessionId,
                            JingleConnectionManager.DeviceDiscoveryState.DISCOVERED);
        } else {
            this.service.markMessage(
                    account,
                    from.asBareJid(),
                    id,
                    eu.siacs.conversations.entities.Message.STATUS_SEND_RECEIVED);
        }
    }

    public void processRequest(final Message packet, final MessageArchiveManager.Query query) {
        final var remoteMsgId = packet.getId();
        final var request = packet.hasExtension(Request.class);
        if (query == null) {
            if (request) {
                received(packet.getFrom(), remoteMsgId, packet.getType());
            }
        } else if (query.isCatchup()) { // TODO only for non group chat?
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), remoteMsgId));
            }
        }
    }

    public void received(final Jid to, final String id) {
        received(to, id, Message.Type.NORMAL);
    }

    private void received(final Jid to, final String id, final Message.Type type) {
        final var message = new Message();
        message.setType(type);
        message.setTo(to);
        message.addExtension(new Received(id));
        message.addExtension(new Store());
        this.connection.sendMessagePacket(message);
    }
}
