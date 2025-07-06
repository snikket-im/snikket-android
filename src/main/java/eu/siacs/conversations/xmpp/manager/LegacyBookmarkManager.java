package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.model.Bookmark;
import im.conversations.android.model.ImmutableBookmark;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.bookmark.Conference;
import im.conversations.android.xmpp.model.bookmark.Storage;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class LegacyBookmarkManager extends AbstractBookmarkManager {

    public LegacyBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
    }

    public void handleItems(final Items items) {
        Log.d(Config.LOGTAG, "LegacyBookmarkManager.handleItems(" + items + ")");
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
            final Map<Jid, Bookmark> bookmarks = storageToBookmarks(storage, account);
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
        final var storage = asStorage(bookmarks);
        return getManager(PepManager.class).publishSingleton(storage, NodeConfiguration.WHITELIST);
    }

    public static Storage asStorage(final Collection<Bookmark> bookmarks) {
        final var storage = new Storage();
        for (final var bookmark : bookmarks) {
            storage.addExtension(asConference(bookmark));
        }
        return storage;
    }

    private static Conference asConference(final Bookmark bookmark) {
        final var conference = new Conference();
        conference.setJid(bookmark.getAddress());
        conference.setAutoJoin(bookmark.isAutoJoin());
        conference.setNick(bookmark.getNick());
        conference.setConferenceName(bookmark.getName());
        conference.setPassword(bookmark.getPassword());
        return conference;
    }

    public static Map<Jid, Bookmark> storageToBookmarks(
            final Storage storage, final Account account) {
        if (storage == null) {
            return Collections.emptyMap();
        }
        Log.d(Config.LOGTAG, "<-- " + storage);
        final var builder = new ImmutableMap.Builder<Jid, Bookmark>();
        for (final var conference : storage.getExtensions(Conference.class)) {
            final Bookmark bookmark = conferenceToBookmark(conference, account);
            if (bookmark != null) {
                builder.put(bookmark.getAddress(), bookmark);
            }
        }
        return builder.buildKeepingLast();
    }

    private static Bookmark conferenceToBookmark(
            final Conference conference, final Account account) {
        final var address = Jid.Invalid.getNullForInvalid(conference.getJid());
        if (address == null) {
            return null;
        }
        try {
            return ImmutableBookmark.builder()
                    .account(account)
                    .address(address)
                    .name(conference.getConferenceName())
                    .isAutoJoin(conference.isAutoJoin())
                    .nick(conference.getNick())
                    .password(conference.getPassword())
                    .build();
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "could not parse bookmark", e);
            return null;
        }
    }
}
