package im.conversations.android.xmpp.processor;

import static eu.siacs.conversations.utils.Random.SECURE_RANDOM;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.http.ServiceOutageStatus;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AccountStateProcessor extends XmppConnection.Delegate
        implements Consumer<Account.State> {

    private final XmppConnectionService service;

    public AccountStateProcessor(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    @Override
    public void accept(final Account.State status) {
        final var account = getAccount();
        if (ServiceOutageStatus.isPossibleOutage(status)) {
            this.service.fetchServiceOutageStatus(account);
        }
        this.service.updateAccountUi();

        if (account.getStatus() == Account.State.ONLINE || account.getStatus().isError()) {
            this.service.getQuickConversationsService().signalAccountStateChange();
        }

        if (account.getStatus() == Account.State.ONLINE) {
            synchronized (this.service.mLowPingTimeoutMode) {
                if (this.service.mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid() + ": leaving low ping timeout mode");
                }
            }
            if (account.setShowErrorNotification(true)) {
                this.service.databaseBackend.updateAccount(account);
            }
            this.service.getMessageArchiveService().executePendingQueries(account);
            if (connection != null && connection.getFeatures().csi()) {
                if (this.service.checkListeners()) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//inactive");
                    connection.sendInactive();
                } else {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//active");
                    connection.sendActive();
                }
            }
            List<Conversation> conversations = this.service.getConversations();
            for (Conversation conversation : conversations) {
                final boolean inProgressJoin;
                synchronized (account.inProgressConferenceJoins) {
                    inProgressJoin = account.inProgressConferenceJoins.contains(conversation);
                }
                final boolean pendingJoin;
                synchronized (account.pendingConferenceJoins) {
                    pendingJoin = account.pendingConferenceJoins.contains(conversation);
                }
                if (conversation.getAccount() == account && !pendingJoin && !inProgressJoin) {
                    this.service.sendUnsentMessages(conversation);
                }
            }
            final List<Conversation> pendingLeaves;
            synchronized (account.pendingConferenceLeaves) {
                pendingLeaves = new ArrayList<>(account.pendingConferenceLeaves);
                account.pendingConferenceLeaves.clear();
            }
            for (Conversation conversation : pendingLeaves) {
                this.service.leaveMuc(conversation);
            }
            final List<Conversation> pendingJoins;
            synchronized (account.pendingConferenceJoins) {
                pendingJoins = new ArrayList<>(account.pendingConferenceJoins);
                account.pendingConferenceJoins.clear();
            }
            for (Conversation conversation : pendingJoins) {
                this.service.joinMuc(conversation);
            }
            this.service.scheduleWakeUpCall(
                    Config.PING_MAX_INTERVAL * 1000L, account.getUuid().hashCode());
        } else if (account.getStatus() == Account.State.OFFLINE
                || account.getStatus() == Account.State.DISABLED
                || account.getStatus() == Account.State.LOGGED_OUT) {
            this.service.resetSendingToWaiting(account);
            if (account.isConnectionEnabled() && this.service.isInLowPingTimeoutMode(account)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": went into offline state during low ping mode."
                                + " reconnecting now");
                this.service.reconnectAccount(account, true, false);
            } else {
                final int timeToReconnect = SECURE_RANDOM.nextInt(10) + 2;
                this.service.scheduleWakeUpCall(timeToReconnect, account.getUuid().hashCode());
            }
        } else if (account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL) {
            this.service.databaseBackend.updateAccount(account);
            this.service.reconnectAccount(account, true, false);
        } else if (account.getStatus() != Account.State.CONNECTING
                && account.getStatus() != Account.State.NO_INTERNET) {
            this.service.resetSendingToWaiting(account);
            if (connection != null && account.getStatus().isAttemptReconnect()) {
                final boolean aggressive =
                        account.getStatus() == Account.State.SEE_OTHER_HOST
                                || this.service.hasJingleRtpConnection(account);
                final int next = connection.getTimeToNextAttempt(aggressive);
                final boolean lowPingTimeoutMode = this.service.isInLowPingTimeoutMode(account);
                if (next <= 0) {
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": error connecting account. reconnecting now."
                                    + " lowPingTimeout="
                                    + lowPingTimeoutMode);
                    this.service.reconnectAccount(account, true, false);
                } else {
                    final int attempt = connection.getAttempt() + 1;
                    Log.d(
                            Config.LOGTAG,
                            account.getJid().asBareJid()
                                    + ": error connecting account. try again in "
                                    + next
                                    + "s for the "
                                    + attempt
                                    + " time. lowPingTimeout="
                                    + lowPingTimeoutMode
                                    + ", aggressive="
                                    + aggressive);
                    this.service.scheduleWakeUpCall(next, account.getUuid().hashCode());
                    if (aggressive) {
                        this.service.internalPingExecutor.schedule(
                                service::manageAccountConnectionStatesInternal,
                                (next * 1000L) + 50,
                                TimeUnit.MILLISECONDS);
                    }
                }
            }
        }
        this.service.getNotificationService().updateErrorNotification();
    }
}
