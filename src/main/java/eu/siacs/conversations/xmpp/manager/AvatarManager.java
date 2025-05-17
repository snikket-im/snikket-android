package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.pubsub.Items;

public class AvatarManager extends AbstractManager {

    public AvatarManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleItems(Jid from, final Items items) {}
}
