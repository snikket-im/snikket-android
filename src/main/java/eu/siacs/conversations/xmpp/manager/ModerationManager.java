package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.moderation.Moderate;
import im.conversations.android.xmpp.model.retraction.Retract;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;

public class ModerationManager extends AbstractManager {

    private final XmppConnectionService service;

    public ModerationManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<Void> moderate(final eu.siacs.conversations.entities.Message message) {
        final var conversation = message.getConversation();
        final var address = conversation.getAddress().asBareJid();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var moderate = iq.addExtension(new Moderate(message.getServerMsgId()));
        moderate.addExtension(new Retract());
        final var future = this.connection.sendIqPacket(iq);
        return Futures.transform(
                future,
                result -> {
                    if (message.getConversation() instanceof Conversation c) {
                        c.remove(message);
                        if (getDatabase().deleteMessage(message.getUuid())) {
                            Log.d(Config.LOGTAG, "deleted local copy of moderated message");
                        }
                        this.service.updateConversationUi();
                        return null;
                    } else {
                        throw new IllegalStateException("Message was not part of conversation");
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void handleRetraction(final Message message) {
        final var account = getAccount();
        final var from = Jid.Invalid.getNullForInvalid(message.getFrom());
        if (from == null || from.isFullJid() || message.getType() != Message.Type.GROUPCHAT) {
            Log.d(
                    Config.LOGTAG,
                    "received retraction from "
                            + from
                            + " but retractions are only supported in MUC");
            return;
        }
        final var mucOptions = getManager(MultiUserChatManager.class).getState(from.asBareJid());
        if (mucOptions == null) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": received retraction in MUC w/o state");
            return;
        }
        if (mucOptions.isPrivateAndNonAnonymous()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": retractions are only supported in public channels");
            return;
        }
        final var retraction = message.getExtension(Retract.class);
        final var stanzaId = retraction == null ? null : retraction.getId();
        if (stanzaId == null) {
            Log.d(Config.LOGTAG, "retraction was missing stanza-id");
            return;
        }
        final var conversation = mucOptions.getConversation();
        final var retractedMessage = conversation.findMessageWithServerMsgId(stanzaId);
        if (retractedMessage == null) {
            Log.d(Config.LOGTAG, "received retraction for " + stanzaId + ". Message not found.");
            return;
        }
        conversation.remove(retractedMessage);
        this.service.getNotificationService().clear(retractedMessage);
        if (getDatabase().deleteMessage(retractedMessage.getUuid())) {
            Log.d(Config.LOGTAG, "received retraction for " + stanzaId + " in " + from);
        }
        this.service.updateConversationUi();
    }

    private void removeMessageFromConversation(
            final eu.siacs.conversations.entities.Message message) {}
}
