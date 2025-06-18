package im.conversations.android.xmpp.processor;

import android.util.Log;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.HttpUploadManager;
import eu.siacs.conversations.xmpp.manager.LegacyBookmarkManager;
import eu.siacs.conversations.xmpp.manager.MessageDisplayedSynchronizationManager;
import eu.siacs.conversations.xmpp.manager.NickManager;
import eu.siacs.conversations.xmpp.manager.PrivateStorageManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import im.conversations.android.xmpp.model.stanza.Iq;

public class BindProcessor extends XmppConnection.Delegate implements Runnable {

    private final XmppConnectionService service;

    public BindProcessor(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    @Override
    public void run() {
        final var account = connection.getAccount();
        final var features = connection.getFeatures();
        final boolean loggedInSuccessfully =
                account.setOption(Account.OPTION_LOGGED_IN_SUCCESSFULLY, true);
        final boolean sosModified;
        final var sos = features.getServiceOutageStatus();
        if (sos != null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + " server has SOS on " + sos);
            sosModified = account.setKey(Account.KEY_SOS_URL, sos.toString());
        } else {
            sosModified = false;
        }
        final boolean gainedFeature =
                account.setOption(
                        Account.OPTION_HTTP_UPLOAD_AVAILABLE,
                        getManager(HttpUploadManager.class).isAvailableForSize(0));
        if (loggedInSuccessfully || gainedFeature || sosModified) {
            service.databaseBackend.updateAccount(account);
        }

        if (loggedInSuccessfully) {
            final String displayName = account.getDisplayName();
            if (!Strings.isNullOrEmpty(displayName)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": display name wasn't empty on first log in. publishing");
                getManager(NickManager.class).publish(displayName);
            }
        }

        getManager(RosterManager.class).clearPresences();
        synchronized (account.inProgressConferenceJoins) {
            account.inProgressConferenceJoins.clear();
        }
        synchronized (account.inProgressConferencePings) {
            account.inProgressConferencePings.clear();
        }
        service.getJingleConnectionManager().notifyRebound(account);
        service.getQuickConversationsService().considerSyncBackground(false);

        getManager(RosterManager.class).request();

        if (getManager(BookmarkManager.class).hasFeature()) {
            getManager(BookmarkManager.class).fetch();
        } else if (getManager(LegacyBookmarkManager.class).hasConversion()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid() + ": not fetching bookmarks. waiting for server to push");
        } else {
            getManager(PrivateStorageManager.class).fetchBookmarks();
        }

        if (features.mds()) {
            getManager(MessageDisplayedSynchronizationManager.class).fetch();
        } else {
            Log.d(Config.LOGTAG, account.getJid() + ": server has no support for mds");
        }
        final boolean bind2 = features.bind2();
        final boolean flexible = features.flexibleOfflineMessageRetrieval();
        final boolean catchup = service.getMessageArchiveService().inCatchup(account);
        final boolean trackOfflineMessageRetrieval;
        if (!bind2 && flexible && catchup && connection.isMamPreferenceAlways()) {
            trackOfflineMessageRetrieval = false;
            connection.sendIqPacket(
                    IqGenerator.purgeOfflineMessages(),
                    (packet) -> {
                        if (packet.getType() == Iq.Type.RESULT) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully purged offline messages");
                        }
                    });
        } else {
            trackOfflineMessageRetrieval = true;
        }
        service.sendPresence(account);
        connection.trackOfflineMessageRetrieval(trackOfflineMessageRetrieval);
        if (service.getPushManagementService().available(account)) {
            service.getPushManagementService().registerPushTokenOnServer(account);
        }
        service.connectMultiModeConversations(account);
        getManager(RosterManager.class).syncDirtyContacts();

        service.getUnifiedPushBroker().renewUnifiedPushEndpointsOnBind(account);
    }
}
