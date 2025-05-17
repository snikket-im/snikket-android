package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.bookmark2.Conference;
import im.conversations.android.xmpp.model.bookmark2.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Map;

public class BookmarkManager extends AbstractBookmarkManager {

    public BookmarkManager(final XmppConnectionService service, XmppConnection connection) {
        super(service, connection);
    }

    public void fetch() {
        final var future = getManager(PepManager.class).fetchItems(Conference.class);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Map<String, Conference> bookmarks) {
                        final var builder = new ImmutableMap.Builder<Jid, Bookmark>();
                        for (final var entry : bookmarks.entrySet()) {
                            final Bookmark bookmark =
                                    Bookmark.parseFromItem(
                                            entry.getKey(), entry.getValue(), getAccount());
                            if (bookmark == null) {
                                continue;
                            }
                            builder.put(bookmark.getJid(), bookmark);
                        }
                        processBookmarksInitial(builder.buildKeepingLast(), true);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        Log.d(Config.LOGTAG, "Could not fetch bookmarks", throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void handleItems(final Items items) {
        final var retractions = items.getRetractions();
        final var itemMap = items.getItemMap(Conference.class);
        if (!retractions.isEmpty()) {
            // deleteItems(retractions);
        }
        if (!itemMap.isEmpty()) {
            // updateItems(itemMap);
        }
    }

    public ListenableFuture<Void> publishBookmark(final Jid address, final boolean autoJoin) {
        return publishBookmark(address, autoJoin, null);
    }

    public ListenableFuture<Void> publishBookmark(
            final Jid address, final boolean autoJoin, final String nick) {
        final var itemId = address.toString();
        final var conference = new Conference();
        conference.setAutoJoin(autoJoin);
        if (nick != null) {
            conference.addExtension(new Nick()).setContent(nick);
        }
        return Futures.transform(
                getManager(PepManager.class)
                        .publish(conference, itemId, NodeConfiguration.WHITELIST_MAX_ITEMS),
                result -> null,
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> retractBookmark(final Jid address) {
        final var itemId = address.toString();
        return Futures.transform(
                getManager(PepManager.class).retract(itemId, Namespace.BOOKMARKS2),
                result -> null,
                MoreExecutors.directExecutor());
    }

    public void deleteAllItems() {}
}
