package im.conversations.android.xmpp.processor;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.BookmarkManager;
import eu.siacs.conversations.xmpp.manager.HttpUploadManager;
import eu.siacs.conversations.xmpp.manager.MessageDisplayedSynchronizationManager;
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager;
import eu.siacs.conversations.xmpp.manager.NickManager;
import eu.siacs.conversations.xmpp.manager.OfflineMessagesManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import eu.siacs.conversations.xmpp.manager.RosterManager;

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

        getManager(PresenceManager.class).clear();
        getManager(MultiUserChatManager.class).clearInProgress();
        service.getJingleConnectionManager().notifyRebound(account);
        service.getQuickConversationsService().considerSyncBackground(false);

        getManager(RosterManager.class).request();
        getManager(BookmarkManager.class).request();

        if (features.mds()) {
            getManager(MessageDisplayedSynchronizationManager.class).fetch();
        } else {
            Log.d(Config.LOGTAG, account.getJid() + ": server has no support for mds");
        }
        final var offlineManager = getManager(OfflineMessagesManager.class);
        final boolean bind2 = features.bind2();
        final boolean flexible = offlineManager.hasFeature();
        final boolean catchup = service.getMessageArchiveService().inCatchup(account);
        final boolean trackOfflineMessageRetrieval;
        if (!bind2 && flexible && catchup && connection.isMamPreferenceAlways()) {
            trackOfflineMessageRetrieval = false;
            Futures.addCallback(
                    offlineManager.purge(),
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(Void result) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully purged offline messages");
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not purge offline messages", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        } else {
            trackOfflineMessageRetrieval = true;
        }
        getManager(PresenceManager.class).available();
        connection.trackOfflineMessageRetrieval(trackOfflineMessageRetrieval);
        if (service.getPushManagementService().available(account)) {
            service.getPushManagementService().registerPushTokenOnServer(account);
        }
        service.connectMultiModeConversations(account);
        getManager(RosterManager.class).syncDirtyContacts();

        service.getUnifiedPushBroker().renewUnifiedPushEndpointsOnBind(account);
    }
}
