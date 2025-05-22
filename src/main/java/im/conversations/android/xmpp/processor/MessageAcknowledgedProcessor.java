package im.conversations.android.xmpp.processor;

import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import java.util.function.BiFunction;

public class MessageAcknowledgedProcessor extends XmppConnection.Delegate
        implements BiFunction<Jid, String, Boolean> {

    private final XmppConnectionService service;

    public MessageAcknowledgedProcessor(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    @Override
    public Boolean apply(final Jid to, final String id) {
        if (id.startsWith(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX)) {
            final String sessionId =
                    id.substring(JingleRtpConnection.JINGLE_MESSAGE_PROPOSE_ID_PREFIX.length());
            this.service
                    .getJingleConnectionManager()
                    .updateProposedSessionDiscovered(
                            getAccount(),
                            to,
                            sessionId,
                            JingleConnectionManager.DeviceDiscoveryState.SEARCHING_ACKNOWLEDGED);
        }

        final Jid bare = to.asBareJid();

        for (final Conversation conversation : service.getConversations()) {
            if (conversation.getAccount() == getAccount()
                    && conversation.getJid().asBareJid().equals(bare)) {
                final Message message = conversation.findUnsentMessageWithUuid(id);
                if (message != null) {
                    message.setStatus(Message.STATUS_SEND);
                    message.setErrorMessage(null);
                    getDatabase().updateMessage(message, false);
                    return true;
                }
            }
        }
        return false;
    }
}
