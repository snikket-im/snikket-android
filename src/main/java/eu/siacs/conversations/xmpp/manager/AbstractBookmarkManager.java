package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class AbstractBookmarkManager extends AbstractManager {

    private final XmppConnectionService service;

    protected AbstractBookmarkManager(
            final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.service = service;
    }

    // TODO rename to setBookmarks?
    public void processBookmarksInitial(final Map<Jid, Bookmark> bookmarks, final boolean pep) {
        final var account = getAccount();
        // TODO we can internalize this getBookmarkedJid
        final Set<Jid> previousBookmarks = account.getBookmarkedJids();
        for (final Bookmark bookmark : bookmarks.values()) {
            previousBookmarks.remove(bookmark.getJid().asBareJid());
            service.processModifiedBookmark(bookmark, pep);
        }
        if (pep) {
            this.processDeletedBookmarks(account, previousBookmarks);
        }
        account.setBookmarks(bookmarks);
    }

    public void processDeletedBookmarks(final Account account, final Collection<Jid> bookmarks) {
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": "
                        + bookmarks.size()
                        + " bookmarks have been removed");
        for (final Jid bookmark : bookmarks) {
            processDeletedBookmark(account, bookmark);
        }
    }

    public void processDeletedBookmark(final Account account, final Jid jid) {
        final Conversation conversation = service.find(account, jid);
        if (conversation == null) {
            return;
        }
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid() + ": archiving MUC " + jid + " after PEP update");
        this.service.archiveConversation(conversation, false);
    }
}
