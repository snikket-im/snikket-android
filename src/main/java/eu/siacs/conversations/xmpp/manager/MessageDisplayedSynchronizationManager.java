package eu.siacs.conversations.xmpp.manager;

import android.util.Log;

import androidx.annotation.NonNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.mds.Displayed;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Map;

public class MessageDisplayedSynchronizationManager extends AbstractManager {

    private final XmppConnectionService service;

    public MessageDisplayedSynchronizationManager(
            final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void handleItems(final Items items) {
        for (final var item : items.getItemMap(Displayed.class).entrySet()) {
            this.processMdsItem(item);
        }
    }

    public void processMdsItem(final Map.Entry<String, Displayed> item) {
        final var account = getAccount();
        final Jid jid = Jid.Invalid.getNullForInvalid(Jid.ofOrInvalid(item.getKey()));
        if (jid == null) {
            return;
        }
        final var displayed = item.getValue();
        final var stanzaId = displayed.getStanzaId();
        final String id = stanzaId == null ? null : stanzaId.getId();
        final Conversation conversation = this.service.find(account, jid);
        if (id != null && conversation != null) {
            conversation.setDisplayState(id);
            this.service.markReadUpToStanzaId(conversation, id);
        }
    }

    public void fetch() {
        final var future = getManager(PepManager.class).fetchItems(Displayed.class);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Map<String, Displayed> result) {
                        for (final var entry : result.entrySet()) {
                            processMdsItem(entry);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG,getAccount().getJid().asBareJid()+": could not retrieve MDS items", t);
                    }
                },
                MoreExecutors.directExecutor());
    }
}
