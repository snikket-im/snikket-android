package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.bookmark.Storage;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Map;

public class LegacyBookmarkManager extends AbstractBookmarkManager {

    public LegacyBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    public void handleItems(final Items items) {
        final var account = this.getAccount();
        final var connection = this.connection;
        if (connection.getFeatures().bookmarksConversion()) {
            if (connection.getFeatures().bookmarks2()) {
                Log.w(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": received storage:bookmark notification even though we"
                                + " opted into bookmarks:1");
            }
            final var storage = items.getFirstItem(Storage.class);
            final Map<Jid, Bookmark> bookmarks = Bookmark.parseFromStorage(storage, account);
            this.processBookmarksInitial(bookmarks, true);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": processing bookmark PEP event");
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": ignoring bookmark PEP event because bookmark conversion was"
                            + " not detected");
        }
    }
}
