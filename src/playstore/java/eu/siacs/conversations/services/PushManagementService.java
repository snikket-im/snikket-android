package eu.siacs.conversations.services;

import android.util.Log;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailabilityLight;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.messaging.FirebaseMessaging;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.PhoneHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.manager.PushNotificationManager;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Objects;
import org.jspecify.annotations.NonNull;

public class PushManagementService {

    protected final XmppConnectionService mXmppConnectionService;

    PushManagementService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private static Data findResponseData(Iq response) {
        final Element command = response.findChild("command", Namespace.COMMANDS);
        final Element x = command == null ? null : command.findChild("x", Namespace.DATA);
        return x == null ? null : Data.parse(x);
    }

    private Jid getAppServer() {
        return Jid.of(mXmppConnectionService.getString(R.string.app_server));
    }

    public void registerPushTokenOnServer(final Account account) {
        final var pushManager =
                account.getXmppConnection().getManager(PushNotificationManager.class);
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": has push support");
        final var fcmTokenFuture = retrieveFcmInstanceToken();
        final var registrationFuture =
                Futures.transformAsync(
                        fcmTokenFuture,
                        fcmToken -> {
                            final var androidId = PhoneHelper.getAndroidId(mXmppConnectionService);
                            final var appServer = getAppServer();
                            return pushManager.register(appServer, fcmToken, androidId);
                        },
                        MoreExecutors.directExecutor());
        final var enableFuture =
                Futures.transformAsync(
                        registrationFuture, pushManager::enable, MoreExecutors.directExecutor());
        Futures.addCallback(
                enableFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(
                                Config.LOGTAG,
                                account.getJid().asBareJid() + ": successfully enabled push");
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(Config.LOGTAG, "could not register for push", t);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private @NonNull ListenableFuture<String> retrieveFcmInstanceToken() {
        final FirebaseMessaging firebaseMessaging;
        try {
            firebaseMessaging = FirebaseMessaging.getInstance();
        } catch (final IllegalStateException e) {
            return Futures.immediateFailedFuture(e);
        }
        final var task = firebaseMessaging.getToken();
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    task.addOnCompleteListener(
                            completedTask -> {
                                if (completedTask.isCanceled()) {
                                    completer.setCancelled();
                                } else if (completedTask.isSuccessful()) {
                                    completer.set(completedTask.getResult());
                                } else {
                                    final var e = completedTask.getException();
                                    completer.setException(
                                            Objects.requireNonNullElseGet(
                                                    e, IllegalStateException::new));
                                }
                            });
                    return null;
                });
    }

    public boolean available(final Account account) {
        final XmppConnection connection = account.getXmppConnection();
        return connection != null
                && connection.getFeatures().sm()
                && connection.getManager(PushNotificationManager.class).hasFeature()
                && playServicesAvailable();
    }

    private boolean playServicesAvailable() {
        return GoogleApiAvailabilityLight.getInstance()
                        .isGooglePlayServicesAvailable(mXmppConnectionService)
                == ConnectionResult.SUCCESS;
    }

    public boolean isStub() {
        return false;
    }
}
