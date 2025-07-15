package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.model.Bookmark;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class AbstractBookmarkManager extends AbstractManager {

    protected final XmppConnectionService service;

    protected AbstractBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.service = service;
    }

    protected void setBookmarks(final Map<Jid, Bookmark> bookmarks, final boolean pep) {
        final var manager = getManager(BookmarkManager.class);
        // leaving MUCs on bookmark deletion doesn't work on clean start because 'previous
        // bookmarks' will be empty and we can't get the diff for which bookmarks have been deleted
        // vs never existed. this could be circumvented by persisting bookmarks across restarts
        final Set<Jid> previousBookmarks = manager.getBookmarkAddresses();
        for (final Bookmark bookmark : bookmarks.values()) {
            previousBookmarks.remove(bookmark.getAddress().asBareJid());
            manager.processModifiedBookmark(bookmark, pep);
        }
        if (pep) {
            this.processDeletedBookmarks(previousBookmarks);
        }
        manager.setBookmarks(bookmarks);
    }

    protected void processDeletedBookmarks(final Collection<Jid> bookmarks) {
        Log.d(
                Config.LOGTAG,
                getAccount().getJid().asBareJid()
                        + ": "
                        + bookmarks.size()
                        + " bookmarks have been removed");
        for (final Jid bookmark : bookmarks) {
            processDeletedBookmark(bookmark);
        }
    }

    protected void processDeletedBookmark(final Jid jid) {
        final Conversation conversation = service.find(getAccount(), jid);
        if (conversation == null) {
            return;
        }
        Log.d(
                Config.LOGTAG,
                getAccount().getJid().asBareJid() + ": archiving MUC " + jid + " after PEP update");
        this.service.archiveConversation(conversation, false);
    }
}
