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
import im.conversations.android.xmpp.model.bookmark2.Password;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.pubsub.event.Retract;
import java.util.Collection;
import java.util.Collections;
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
        this.handleItems(items.getItemMap(Conference.class));
        this.handleRetractions(items.getRetractions());
    }

    private void handleRetractions(final Collection<Retract> retractions) {
        final var account = getAccount();
        for (final var retract : retractions) {
            final Jid id = Jid.Invalid.getNullForInvalid(retract.getAttributeAsJid("id"));
            if (id != null) {
                account.removeBookmark(id);
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted bookmark for " + id);
                processDeletedBookmark(id);
                service.updateConversationUi();
            }
        }
    }

    private void handleItems(final Map<String, Conference> items) {
        final var account = getAccount();
        for (final var item : items.entrySet()) {
            // TODO parseFromItem can be included in this Manager
            final Bookmark bookmark =
                    Bookmark.parseFromItem(item.getKey(), item.getValue(), account);
            if (bookmark == null) {
                continue;
            }
            account.putBookmark(bookmark);
            service.processModifiedBookmark(bookmark);
            service.updateConversationUi();
        }
    }

    public ListenableFuture<Void> publish(final Bookmark bookmark) {
        final var address = bookmark.getJid();
        final var name = bookmark.getBookmarkName();
        final var nick = bookmark.getNick();
        final String password = bookmark.getPassword();
        final var itemId = address.toString();
        final var conference = new Conference();
        conference.setAutoJoin(bookmark.autojoin());
        if (nick != null) {
            conference.addExtension(new Nick()).setContent(nick);
        }
        if (name != null) {
            conference.setConferenceName(name);
        }
        if (password != null) {
            conference.addExtension(new Password()).setContent(password);
        }
        conference.addExtension(bookmark.getExtensions());
        return Futures.transform(
                getManager(PepManager.class)
                        .publish(conference, itemId, NodeConfiguration.WHITELIST_MAX_ITEMS),
                result -> null,
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> retract(final Jid address) {
        final var itemId = address.toString();
        return Futures.transform(
                getManager(PepManager.class).retract(itemId, Namespace.BOOKMARKS2),
                result -> null,
                MoreExecutors.directExecutor());
    }

    private void deleteAllItems() {
        final var account = getAccount();
        final var previous = account.getBookmarkedJids();
        account.setBookmarks(Collections.emptyMap());
        processDeletedBookmarks(previous);
    }

    public void handleDelete() {
        Log.d(Config.LOGTAG, getAccount().getJid().asBareJid() + ": deleted bookmarks node");
        this.deleteAllItems();
    }

    public void handlePurge() {
        Log.d(Config.LOGTAG, getAccount().getJid().asBareJid() + ": purged bookmarks");
        this.deleteAllItems();
    }

    public boolean hasFeature() {
        final var pep = getManager(PepManager.class);
        final var disco = getManager(DiscoManager.class);
        return pep.hasPublishOptions()
                && pep.hasConfigNodeMax()
                && disco.hasAccountFeature(Namespace.BOOKMARKS2_COMPAT);
    }
}
