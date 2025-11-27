package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.offline.Offline;
import im.conversations.android.xmpp.model.offline.Purge;
import im.conversations.android.xmpp.model.stanza.Iq;

public class OfflineMessagesManager extends AbstractManager {

    public OfflineMessagesManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Void> purge() {
        final var iq = new Iq(Iq.Type.SET);
        iq.addExtension(new Offline()).addExtension(new Purge());
        final var future = connection.sendIqPacket(iq);
        return Futures.transform(future, result -> null, MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class)
                .hasServerFeature(Namespace.FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL);
    }
}
