package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.bookmark.Storage;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Collection;
import java.util.Map;

public class LegacyBookmarkManager extends AbstractBookmarkManager {

    public LegacyBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    public void handleItems(final Items items) {
        final var account = this.getAccount();
        if (this.hasConversion()) {
            if (getManager(NativeBookmarkManager.class).hasFeature()) {
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

    public boolean hasConversion() {
        return getManager(PepManager.class).hasPublishOptions()
                && getManager(DiscoManager.class).hasAccountFeature(Namespace.BOOKMARKS_CONVERSION);
    }

    public ListenableFuture<Void> publish(final Collection<Bookmark> bookmarks) {
        final var storage = new Storage();
        for (final var bookmark : bookmarks) {
            storage.addChild(bookmark);
        }
        return getManager(PepManager.class).publishSingleton(storage, NodeConfiguration.WHITELIST);
    }
}
