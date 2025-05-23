package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.bookmark.Storage;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.storage.PrivateStorage;
import java.util.Collection;
import java.util.Map;

public class PrivateStorageManager extends AbstractBookmarkManager {

    public PrivateStorageManager(final XmppConnectionService service, XmppConnection connection) {
        super(service, connection);
    }

    public void fetchBookmarks() {
        final var iq = new Iq(Iq.Type.GET);
        final var privateStorage = iq.addExtension(new PrivateStorage());
        privateStorage.addExtension(new Storage());
        final var future = this.connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(Iq result) {
                        final var privateStorage = result.getExtension(PrivateStorage.class);
                        if (privateStorage == null) {
                            return;
                        }
                        final var bookmarkStorage = privateStorage.getExtension(Storage.class);
                        final Map<Jid, Bookmark> bookmarks =
                                Bookmark.parseFromStorage(bookmarkStorage, getAccount());
                        processBookmarksInitial(bookmarks, false);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not fetch bookmark from private storage",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> publishBookmarks(Collection<Bookmark> bookmarks) {
        final var iq = new Iq(Iq.Type.SET);
        final var privateStorage = iq.addExtension(new PrivateStorage());
        final var storage = privateStorage.addExtension(new Storage());
        for (final var bookmark : bookmarks) {
            storage.addChild(bookmark);
        }
        return Futures.transform(
                connection.sendIqPacket(iq), result -> null, MoreExecutors.directExecutor());
    }
}
