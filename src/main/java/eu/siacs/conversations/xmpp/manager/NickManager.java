package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;

public class NickManager extends AbstractManager {

    public NickManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(final Jid from, Items items) {
        final var item = items.getFirstItem(Nick.class);
        final var nick = item == null ? null : item.getContent();
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return;
        }
    }

    public ListenableFuture<Void> publishNick(final String name) {
        final Nick nick = new Nick();
        nick.setContent(name);
        return getManager(PepManager.class).publishSingleton(nick, NodeConfiguration.PRESENCE);
    }
}
