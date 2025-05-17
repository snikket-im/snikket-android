package eu.siacs.conversations.xmpp.manager;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.pubsub.Items;

public class LegacyBookmarkManager extends AbstractBookmarkManager {

    public LegacyBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    public void handleItems(final Items items) {}
}
