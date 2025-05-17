package im.conversations.android.xmpp.processor;

import android.text.TextUtils;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
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
        Log.d(Config.LOGTAG, "begin onBind()");
        final var account = connection.getAccount();
        final var features = connection.getFeatures();
        service.cancelAvatarFetches(account);
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
                account.setOption(Account.OPTION_HTTP_UPLOAD_AVAILABLE, features.httpUpload(0));
        if (loggedInSuccessfully || gainedFeature || sosModified) {
            service.databaseBackend.updateAccount(account);
        }

        if (loggedInSuccessfully) {
            if (!TextUtils.isEmpty(account.getDisplayName())) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": display name wasn't empty on first log in. publishing");
                service.publishDisplayName(account);
            }
        }

        connection.getManager(RosterManager.class).clearPresences();
        synchronized (account.inProgressConferenceJoins) {
            account.inProgressConferenceJoins.clear();
        }
        synchronized (account.inProgressConferencePings) {
            account.inProgressConferencePings.clear();
        }
        service.getJingleConnectionManager().notifyRebound(account);
        service.getQuickConversationsService().considerSyncBackground(false);

        getManager(RosterManager.class).request();

        if (features.bookmarks2()) {
            connection.getManager(BookmarkManager.class).fetch();
            // log that we use bookmarks 1 and wait for +notify
        } else if (!features.bookmarksConversion()) {
            connection.getManager(PrivateStorageManager.class).fetchBookmarks();
        }

        if (features.mds()) {
            service.fetchMessageDisplayedSynchronization(account);
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
        connection.getManager(RosterManager.class).syncDirtyContacts();

        service.getUnifiedPushBroker().renewUnifiedPushEndpointsOnBind(account);
        Log.d(Config.LOGTAG, "end onBind()");
    }
}
