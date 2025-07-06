package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.model.Bookmark;
import im.conversations.android.model.ImmutableBookmark;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BookmarkManager extends AbstractManager {

    private final XmppConnectionService service;

    private final Map<Jid, Bookmark> bookmarks = new HashMap<>();

    public BookmarkManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void request() {
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            getManager(NativeBookmarkManager.class).fetch();
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            final var account = getAccount();
            Log.d(
                    Config.LOGTAG,
                    account.getJid() + ": not fetching bookmarks. waiting for server to push");
        } else {
            getManager(PrivateStorageManager.class).fetchBookmarks();
        }
    }

    public void save(final Conversation conversation, final String name) {
        final Account account = conversation.getAccount();
        final var address = conversation.getAddress();
        final String nick = Bookmark.nickOfAddress(account, address);
        final var bookmark =
                ImmutableBookmark.builder()
                        .account(account)
                        .address(address.asBareJid())
                        .nick(nick)
                        .name(Strings.emptyToNull(name))
                        .isAutoJoin(true)
                        .build();
        this.create(bookmark);
    }

    public void create(final Bookmark bookmark) {
        this.putBookmark(bookmark);
        final ListenableFuture<Void> future;
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            future = getManager(NativeBookmarkManager.class).publish(bookmark);
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            Log.d(Config.LOGTAG, "pushing via legacy bookmark manager");
            future = getManager(LegacyBookmarkManager.class).publish(this.getBookmarks());
        } else {
            future = getManager(PrivateStorageManager.class).publishBookmarks(this.getBookmarks());
        }
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": bookmark pushed");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not create bookmark",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void delete(final Bookmark bookmark) {
        this.removeBookmark(bookmark);
        final ListenableFuture<Void> future;
        if (getManager(NativeBookmarkManager.class).hasFeature()) {
            future =
                    getManager(NativeBookmarkManager.class)
                            .retract(bookmark.getAddress().asBareJid());
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            future = getManager(LegacyBookmarkManager.class).publish(this.getBookmarks());
        } else {
            future = getManager(PrivateStorageManager.class).publishBookmarks(this.getBookmarks());
        }
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": deleted bookmark");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not delete bookmark",
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void ensureBookmarkIsAutoJoin(final Conversation conversation) {
        final var account = getAccount();
        final var existingBookmark = this.getBookmark(conversation.getAddress());
        if (existingBookmark == null) {
            final var bookmark =
                    ImmutableBookmark.builder()
                            .account(account)
                            .address(conversation.getAddress().asBareJid())
                            .isAutoJoin(true)
                            .build();
            create(bookmark);
        } else {
            if (existingBookmark.isAutoJoin()) {
                return;
            }
            final var modified =
                    ImmutableBookmark.builder().from(existingBookmark).isAutoJoin(true).build();
            this.create(modified);
        }
    }

    public void processModifiedBookmark(final Bookmark bookmark, final boolean pep) {
        final var existing = this.service.find(bookmark);
        if (existing != null) {
            if (existing.getMode() != Conversation.MODE_MULTI) {
                return;
            }
            if (pep && !bookmark.isAutoJoin()) {
                Log.d(
                        Config.LOGTAG,
                        getAccount().getJid().asBareJid()
                                + ": archiving conference ("
                                + existing.getAddress()
                                + ") after receiving pep");
                service.archiveConversation(existing, false);
            } else {
                final MucOptions mucOptions =
                        getManager(MultiUserChatManager.class).getOrCreateState(existing);
                if (mucOptions.getError() == MucOptions.Error.NICK_IN_USE) {
                    final String current = mucOptions.getActualNick();
                    final String proposed = mucOptions.getProposedNickPure();
                    if (current != null && !current.equals(proposed)) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": proposed nick changed after bookmark push "
                                        + current
                                        + "->"
                                        + proposed);
                        getManager(MultiUserChatManager.class).join(existing);
                    }
                } else {
                    getManager(MultiUserChatManager.class).checkMucRequiresRename(existing);
                }
            }
        } else if (bookmark.isAutoJoin()) {
            this.service.findOrCreateConversation(
                    getAccount(), bookmark.getFullAddress(), true, true, false);
        }
    }

    public Collection<Bookmark> getBookmarks() {
        synchronized (this.bookmarks) {
            return ImmutableList.copyOf(this.bookmarks.values());
        }
    }

    public void setBookmarks(final Map<Jid, Bookmark> bookmarks) {
        synchronized (this.bookmarks) {
            this.bookmarks.clear();
            this.bookmarks.putAll(bookmarks);
        }
    }

    public void putBookmark(final Bookmark bookmark) {
        synchronized (this.bookmarks) {
            this.bookmarks.put(bookmark.getAddress(), bookmark);
        }
    }

    public void removeBookmark(final Bookmark bookmark) {
        synchronized (this.bookmarks) {
            this.bookmarks.remove(bookmark.getAddress());
        }
    }

    public void removeBookmark(Jid jid) {
        synchronized (this.bookmarks) {
            this.bookmarks.remove(jid);
        }
    }

    public Set<Jid> getBookmarkAddresses() {
        synchronized (this.bookmarks) {
            return new HashSet<>(this.bookmarks.keySet());
        }
    }

    public Bookmark getBookmark(final Jid jid) {
        synchronized (this.bookmarks) {
            return this.bookmarks.get(jid.asBareJid());
        }
    }
}
