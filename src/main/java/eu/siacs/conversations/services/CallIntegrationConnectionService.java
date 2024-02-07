package eu.siacs.conversations.services;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.ui.RtpSessionActivity;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.Media;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CallIntegrationConnectionService extends ConnectionService {

    private ListenableFuture<ServiceConnectionService> serviceFuture;

    @Override
    public void onCreate() {
        Log.d(Config.LOGTAG, "CallIntegrationService.onCreate()");
        super.onCreate();
        this.serviceFuture = ServiceConnectionService.bindService(this);
    }

    @Override
    public void onDestroy() {
        Log.d(Config.LOGTAG, "destroying CallIntegrationConnectionService");
        super.onDestroy();
        final ServiceConnection serviceConnection;
        try {
            serviceConnection = serviceFuture.get().serviceConnection;
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "could not fetch service connection", e);
            return;
        }
        this.unbindService(serviceConnection);
    }

    private static Connection createOutgoingRtpConnection(
            final XmppConnectionService service,
            final String phoneAccountHandle,
            final Jid with,
            final Set<Media> media) {
        if (service == null) {
            Log.d(
                    Config.LOGTAG,
                    "CallIntegrationConnection service was unable to bind to XmppConnectionService");
            return Connection.createFailedConnection(
                    new DisconnectCause(DisconnectCause.ERROR, "service connection not found"));
        }
        final var account = service.findAccountByUuid(phoneAccountHandle);
        return createOutgoingRtpConnection(service, account, with, media);
    }

    private static Connection createOutgoingRtpConnection(
            @NonNull final XmppConnectionService service,
            @NonNull final Account account,
            final Jid with,
            final Set<Media> media) {
        Log.d(Config.LOGTAG, "create outgoing rtp connection!");
        final Intent intent = new Intent(service, RtpSessionActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(RtpSessionActivity.EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(RtpSessionActivity.EXTRA_WITH, with.toEscapedString());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final Connection callIntegration;
        if (with.isBareJid()) {
            final var contact = account.getRoster().getContact(with);
            if (Config.JINGLE_MESSAGE_INIT_STRICT_OFFLINE_CHECK
                    && contact.getPresences().isEmpty()) {
                intent.putExtra(
                        RtpSessionActivity.EXTRA_LAST_REPORTED_STATE,
                        RtpEndUserState.CONTACT_OFFLINE.toString());
                callIntegration =
                        Connection.createFailedConnection(
                                new DisconnectCause(DisconnectCause.ERROR, "contact is offline"));
                // we can use a JMI 'finish' message to notify the contact of a call we never
                // actually attempted
                // sendJingleFinishMessage(service, contact, Reason.CONNECTIVITY_ERROR);
            } else {
                final var proposal =
                        service.getJingleConnectionManager()
                                .proposeJingleRtpSession(account, with, media);
                intent.putExtra(
                        RtpSessionActivity.EXTRA_LAST_REPORTED_STATE,
                        RtpEndUserState.FINDING_DEVICE.toString());
                callIntegration = proposal.getCallIntegration();
            }
            if (Media.audioOnly(media)) {
                intent.putExtra(
                        RtpSessionActivity.EXTRA_LAST_ACTION,
                        RtpSessionActivity.ACTION_MAKE_VOICE_CALL);
            } else {
                intent.putExtra(
                        RtpSessionActivity.EXTRA_LAST_ACTION,
                        RtpSessionActivity.ACTION_MAKE_VIDEO_CALL);
            }
        } else {
            final JingleRtpConnection jingleRtpConnection =
                    service.getJingleConnectionManager().initializeRtpSession(account, with, media);
            final String sessionId = jingleRtpConnection.getId().sessionId;
            intent.putExtra(RtpSessionActivity.EXTRA_SESSION_ID, sessionId);
            callIntegration = jingleRtpConnection.getCallIntegration();
        }
        service.startActivity(intent);
        return callIntegration;
    }

    private static void sendJingleFinishMessage(
            final XmppConnectionService service, final Contact contact, final Reason reason) {
        service.getJingleConnectionManager()
                .sendJingleMessageFinish(contact, UUID.randomUUID().toString(), reason);
    }

    @Override
    public Connection onCreateOutgoingConnection(
            final PhoneAccountHandle phoneAccountHandle, final ConnectionRequest request) {
        Log.d(Config.LOGTAG, "onCreateOutgoingConnection(" + request.getAddress() + ")");
        final var uri = request.getAddress();
        final var jid = Jid.ofEscaped(uri.getSchemeSpecificPart());
        final var extras = request.getExtras();
        final int videoState = extras.getInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE);
        final Set<Media> media =
                videoState == VideoProfile.STATE_AUDIO_ONLY
                        ? ImmutableSet.of(Media.AUDIO)
                        : ImmutableSet.of(Media.AUDIO, Media.VIDEO);
        Log.d(Config.LOGTAG, "jid=" + jid);
        Log.d(Config.LOGTAG, "phoneAccountHandle:" + phoneAccountHandle.getId());
        Log.d(Config.LOGTAG, "media " + media);
        final var service = ServiceConnectionService.get(this.serviceFuture);
        return createOutgoingRtpConnection(service, phoneAccountHandle.getId(), jid, media);
    }

    @Override
    public Connection onCreateIncomingConnection(
            final PhoneAccountHandle phoneAccountHandle, final ConnectionRequest request) {
        Log.d(Config.LOGTAG, "onCreateIncomingConnection()");
        final var service = ServiceConnectionService.get(this.serviceFuture);
        final Bundle extras = request.getExtras();
        final Bundle extraExtras = extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        final String incomingCallAddress =
                extras.getString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
        final String sid = extraExtras == null ? null : extraExtras.getString("sid");
        Log.d(Config.LOGTAG, "sid " + sid);
        final Uri uri = incomingCallAddress == null ? null : Uri.parse(incomingCallAddress);
        Log.d(Config.LOGTAG, "uri=" + uri);
        if (uri == null || sid == null) {
            return Connection.createFailedConnection(
                    new DisconnectCause(
                            DisconnectCause.ERROR,
                            "connection request is missing required information"));
        }
        if (service == null) {
            return Connection.createFailedConnection(
                    new DisconnectCause(DisconnectCause.ERROR, "service connection not found"));
        }
        final var jid = Jid.ofEscaped(uri.getSchemeSpecificPart());
        final Account account = service.findAccountByUuid(phoneAccountHandle.getId());
        final var weakReference =
                service.getJingleConnectionManager().findJingleRtpConnection(account, jid, sid);
        if (weakReference == null) {
            Log.d(Config.LOGTAG, "no connection found for " + jid + " and sid=" + sid);
            return Connection.createFailedConnection(
                    new DisconnectCause(DisconnectCause.ERROR, "no incoming connection found"));
        }
        final var jingleRtpConnection = weakReference.get();
        if (jingleRtpConnection == null) {
            Log.d(Config.LOGTAG, "connection has been terminated");
            return Connection.createFailedConnection(
                    new DisconnectCause(DisconnectCause.ERROR, "connection has been terminated"));
        }
        Log.d(Config.LOGTAG, "registering call integration for incoming call");
        return jingleRtpConnection.getCallIntegration();
    }

    public static void registerPhoneAccount(final Context context, final Account account) {
        try {
            registerPhoneAccountOrThrow(context, account);
        } catch (final IllegalArgumentException e) {
            Toast.makeText(context, R.string.call_integration_not_available, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static void registerPhoneAccountOrThrow(final Context context, final Account account) {
        final var handle = getHandle(context, account);
        final var telecomManager = context.getSystemService(TelecomManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (telecomManager.getOwnSelfManagedPhoneAccounts().contains(handle)) {
                Log.d(
                        Config.LOGTAG,
                        "a phone account for " + account.getJid().asBareJid() + " already exists");
                return;
            }
        }
        final var builder =
                PhoneAccount.builder(getHandle(context, account), account.getJid().asBareJid());
        builder.setSupportedUriSchemes(Collections.singletonList("xmpp"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCapabilities(
                    PhoneAccount.CAPABILITY_SELF_MANAGED
                            | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING);
        }
        final var phoneAccount = builder.build();
        telecomManager.registerPhoneAccount(phoneAccount);
    }

    public static void registerPhoneAccounts(
            final Context context, final Collection<Account> accounts) {
        for (final Account account : accounts) {
            try {
                registerPhoneAccountOrThrow(context, account);
            } catch (final IllegalArgumentException e) {
                Log.w(
                        Config.LOGTAG,
                        "could not register phone account for " + account.getJid().asBareJid(),
                        e);
                return;
            }
        }
    }

    public static void unregisterPhoneAccount(final Context context, final Account account) {
        context.getSystemService(TelecomManager.class)
                .unregisterPhoneAccount(getHandle(context, account));
    }

    public static PhoneAccountHandle getHandle(final Context context, final Account account) {
        final var competentName =
                new ComponentName(context, CallIntegrationConnectionService.class);
        return new PhoneAccountHandle(competentName, account.getUuid());
    }

    public static void placeCall(
            final XmppConnectionService service,
            final Account account,
            final Jid with,
            final Set<Media> media) {
        if (CallIntegration.selfManaged()) {
            final var extras = new Bundle();
            extras.putParcelable(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, getHandle(service, account));
            extras.putInt(
                    TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                    Media.audioOnly(media)
                            ? VideoProfile.STATE_AUDIO_ONLY
                            : VideoProfile.STATE_BIDIRECTIONAL);
            if (service.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS)
                    != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(service, R.string.no_permission_to_place_call, Toast.LENGTH_SHORT)
                        .show();
                return;
            }
            service.getSystemService(TelecomManager.class)
                    .placeCall(CallIntegration.address(with), extras);
        } else {
            final var connection = createOutgoingRtpConnection(service, account, with, media);
            if (connection != null) {
                Log.d(
                        Config.LOGTAG,
                        "not adding outgoing call to TelecomManager on Android "
                                + Build.VERSION.RELEASE);
            }
        }
    }

    public static void addNewIncomingCall(
            final Context context, final AbstractJingleConnection.Id id) {
        if (CallIntegration.notSelfManaged()) {
            Log.d(
                    Config.LOGTAG,
                    "not adding incoming call to TelecomManager on Android "
                            + Build.VERSION.RELEASE);
            return;
        }
        final var phoneAccountHandle =
                CallIntegrationConnectionService.getHandle(context, id.account);
        final var bundle = new Bundle();
        bundle.putString(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                CallIntegration.address(id.with).toString());
        final var extras = new Bundle();
        extras.putString("sid", id.sessionId);
        bundle.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras);
        context.getSystemService(TelecomManager.class)
                .addNewIncomingCall(phoneAccountHandle, bundle);
    }

    public static class ServiceConnectionService {
        private final ServiceConnection serviceConnection;
        private final XmppConnectionService service;

        public ServiceConnectionService(
                final ServiceConnection serviceConnection, final XmppConnectionService service) {
            this.serviceConnection = serviceConnection;
            this.service = service;
        }

        public static XmppConnectionService get(
                final ListenableFuture<ServiceConnectionService> future) {
            try {
                return future.get(2, TimeUnit.SECONDS).service;
            } catch (final ExecutionException | InterruptedException | TimeoutException e) {
                return null;
            }
        }

        public static ListenableFuture<ServiceConnectionService> bindService(
                final Context context) {
            final SettableFuture<ServiceConnectionService> serviceConnectionFuture =
                    SettableFuture.create();
            final var intent = new Intent(context, XmppConnectionService.class);
            intent.setAction(XmppConnectionService.ACTION_CALL_INTEGRATION_SERVICE_STARTED);
            final var serviceConnection =
                    new ServiceConnection() {

                        @Override
                        public void onServiceConnected(
                                final ComponentName name, final IBinder iBinder) {
                            final XmppConnectionService.XmppConnectionBinder binder =
                                    (XmppConnectionService.XmppConnectionBinder) iBinder;
                            serviceConnectionFuture.set(
                                    new ServiceConnectionService(this, binder.getService()));
                        }

                        @Override
                        public void onServiceDisconnected(final ComponentName name) {}
                    };
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            return serviceConnectionFuture;
        }
    }
}
