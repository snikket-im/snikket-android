package eu.siacs.conversations.services;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.parser.AbstractParser;
import eu.siacs.conversations.persistance.UnifiedPushDatabase;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UnifiedPushBroker {

    // time to expiration before a renewal attempt is made (24 hours)
    public static final long TIME_TO_RENEW = 86_400_000L;

    // interval for the 'cron tob' that attempts renewals for everything that expires is lass than
    // `TIME_TO_RENEW`
    public static final long RENEWAL_INTERVAL = 3_600_000L;

    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    private final XmppConnectionService service;

    public UnifiedPushBroker(final XmppConnectionService xmppConnectionService) {
        this.service = xmppConnectionService;
        SCHEDULER.scheduleAtFixedRate(
                this::renewUnifiedPushEndpoints,
                RENEWAL_INTERVAL,
                RENEWAL_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    public void renewUnifiedPushEndpointsOnBind(final Account account) {
        final Optional<Transport> transportOptional = getTransport();
        if (transportOptional.isPresent()) {
            final Transport transport = transportOptional.get();
            final Account transportAccount = transport.account;
            if (transportAccount != null && transportAccount.getUuid().equals(account.getUuid())) {
                final UnifiedPushDatabase database = UnifiedPushDatabase.getInstance(service);
                if (database.hasEndpoints(transport)) {
                    sendDirectedPresence(transportAccount, transport.transport);
                }
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid() + ": trigger endpoint renewal on bind");
                renewUnifiedEndpoint(transportOptional.get(), null);
            }
        }
    }

    private void sendDirectedPresence(final Account account, Jid to) {
        final PresencePacket presence = new PresencePacket();
        presence.setTo(to);
        service.sendPresencePacket(account, presence);
    }

    public void renewUnifiedPushEndpoints() {
        renewUnifiedPushEndpoints(null);
    }

    public Optional<Transport> renewUnifiedPushEndpoints(@Nullable final PushTargetMessenger pushTargetMessenger) {
        final Optional<Transport> transportOptional = getTransport();
        if (transportOptional.isPresent()) {
            final Transport transport = transportOptional.get();
            if (transport.account.isEnabled()) {
                renewUnifiedEndpoint(transportOptional.get(), pushTargetMessenger);
            } else {
                if (pushTargetMessenger != null && pushTargetMessenger.messenger != null) {
                    sendRegistrationDelayed(pushTargetMessenger.messenger,"account is disabled");
                }
                Log.d(Config.LOGTAG, "skipping UnifiedPush endpoint renewal. Account is disabled");
            }
        } else {
            if (pushTargetMessenger != null && pushTargetMessenger.messenger != null) {
                sendRegistrationDelayed(pushTargetMessenger.messenger,"no transport selected");
            }
            Log.d(Config.LOGTAG, "skipping UnifiedPush endpoint renewal. No transport selected");
        }
        return transportOptional;
    }

    private void sendRegistrationDelayed(final Messenger messenger, final String error) {
        final Intent intent = new Intent(UnifiedPushDistributor.ACTION_REGISTRATION_DELAYED);
        intent.putExtra(UnifiedPushDistributor.EXTRA_MESSAGE, error);
        final var message = new Message();
        message.obj = intent;
        try {
            messenger.send(message);
        } catch (final RemoteException e) {
            Log.d(Config.LOGTAG,"unable to tell messenger of delayed registration",e);
        }
    }

    private void renewUnifiedEndpoint(final Transport transport, final PushTargetMessenger pushTargetMessenger) {
        final Account account = transport.account;
        final UnifiedPushDatabase unifiedPushDatabase = UnifiedPushDatabase.getInstance(service);
        final List<UnifiedPushDatabase.PushTarget> renewals =
                unifiedPushDatabase.getRenewals(
                        account.getUuid(), transport.transport.toEscapedString());
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": "
                        + renewals.size()
                        + " UnifiedPush endpoints scheduled for renewal on "
                        + transport.transport);
        for (final UnifiedPushDatabase.PushTarget renewal : renewals) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": try to renew UnifiedPush " + renewal);
            UnifiedPushDistributor.quickLog(service,String.format("%s: try to renew UnifiedPush %s", account.getJid(), renewal.toString()));
            final String hashedApplication =
                    UnifiedPushDistributor.hash(account.getUuid(), renewal.application);
            final String hashedInstance =
                    UnifiedPushDistributor.hash(account.getUuid(), renewal.instance);
            final IqPacket registration = new IqPacket(IqPacket.TYPE.SET);
            registration.setTo(transport.transport);
            final Element register = registration.addChild("register", Namespace.UNIFIED_PUSH);
            register.setAttribute("application", hashedApplication);
            register.setAttribute("instance", hashedInstance);
            final Messenger messenger;
            if (pushTargetMessenger != null && renewal.equals(pushTargetMessenger.pushTarget)) {
                messenger = pushTargetMessenger.messenger;
            } else {
                messenger = null;
            }
            this.service.sendIqPacket(
                    account,
                    registration,
                    (a, response) -> processRegistration(transport, renewal, messenger, response));
        }
    }

    private void processRegistration(
            final Transport transport,
            final UnifiedPushDatabase.PushTarget renewal,
            final Messenger messenger,
            final IqPacket response) {
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final Element registered = response.findChild("registered", Namespace.UNIFIED_PUSH);
            if (registered == null) {
                return;
            }
            final String endpoint = registered.getAttribute("endpoint");
            if (Strings.isNullOrEmpty(endpoint)) {
                Log.w(Config.LOGTAG, "endpoint was null in up registration");
                return;
            }
            final long expiration;
            try {
                expiration = AbstractParser.getTimestamp(registered.getAttribute("expiration"));
            } catch (final IllegalArgumentException | ParseException e) {
                Log.d(Config.LOGTAG, "could not parse expiration", e);
                return;
            }
            renewUnifiedPushEndpoint(transport, renewal, messenger, endpoint, expiration);
        } else {
            Log.d(Config.LOGTAG, "could not register UP endpoint " + response.getErrorCondition());
        }
    }

    private void renewUnifiedPushEndpoint(
            final Transport transport,
            final UnifiedPushDatabase.PushTarget renewal,
            final Messenger messenger,
            final String endpoint,
            final long expiration) {
        Log.d(Config.LOGTAG, "registered endpoint " + endpoint + " expiration=" + expiration);
        final UnifiedPushDatabase unifiedPushDatabase = UnifiedPushDatabase.getInstance(service);
        final boolean modified =
                unifiedPushDatabase.updateEndpoint(
                        renewal.instance,
                        transport.account.getUuid(),
                        transport.transport.toEscapedString(),
                        endpoint,
                        expiration);
        if (modified) {
            Log.d(
                    Config.LOGTAG,
                    "endpoint for "
                            + renewal.application
                            + "/"
                            + renewal.instance
                            + " was updated to "
                            + endpoint);
            UnifiedPushDistributor.quickLog(
                    service,
                    "endpoint for "
                            + renewal.application
                            + "/"
                            + renewal.instance
                            + " was updated to "
                            + endpoint);
            final UnifiedPushDatabase.ApplicationEndpoint applicationEndpoint =
                    new UnifiedPushDatabase.ApplicationEndpoint(renewal.application, endpoint);
            sendEndpoint(messenger, renewal.instance, applicationEndpoint);
        }
    }

    private void sendEndpoint(final Messenger messenger, String instance, final UnifiedPushDatabase.ApplicationEndpoint applicationEndpoint) {
        if (messenger != null) {
            Log.d(Config.LOGTAG,"using messenger instead of broadcast to communicate endpoint to "+applicationEndpoint.application);
            final Message message = new Message();
            message.obj = endpointIntent(instance, applicationEndpoint);
            try {
                messenger.send(message);
            } catch (final RemoteException e) {
                Log.d(Config.LOGTAG,"messenger failed. falling back to broadcast");
                broadcastEndpoint(instance, applicationEndpoint);
            }
        } else {
            broadcastEndpoint(instance, applicationEndpoint);
        }
    }

    public boolean reconfigurePushDistributor() {
        final boolean enabled = getTransport().isPresent();
        setUnifiedPushDistributorEnabled(enabled);
        if (!enabled) {
            unregisterCurrentPushTargets();
        }
        return enabled;
    }

    private void setUnifiedPushDistributorEnabled(final boolean enabled) {
        final PackageManager packageManager = service.getPackageManager();
        final ComponentName componentName =
                new ComponentName(service, UnifiedPushDistributor.class);
        if (enabled) {
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Log.d(Config.LOGTAG, "UnifiedPushDistributor has been enabled");
        } else {
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            Log.d(Config.LOGTAG, "UnifiedPushDistributor has been disabled");
        }
    }

    private void unregisterCurrentPushTargets() {
        final var future = deletePushTargets();
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(
                            final List<UnifiedPushDatabase.PushTarget> pushTargets) {
                        broadcastUnregistered(pushTargets);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                "could not delete endpoints after UnifiedPushDistributor was disabled");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<UnifiedPushDatabase.PushTarget>> deletePushTargets() {
        return Futures.submit(() -> UnifiedPushDatabase.getInstance(service).deletePushTargets(),SCHEDULER);
    }

    private void broadcastUnregistered(final List<UnifiedPushDatabase.PushTarget> pushTargets) {
        for(final UnifiedPushDatabase.PushTarget pushTarget : pushTargets) {
            Log.d(Config.LOGTAG,"sending unregistered to "+pushTarget);
            broadcastUnregistered(pushTarget);
        }
    }

    private void broadcastUnregistered(final UnifiedPushDatabase.PushTarget pushTarget) {
        final var intent = unregisteredIntent(pushTarget);
        service.sendBroadcast(intent);
    }

    public boolean processPushMessage(
            final Account account, final Jid transport, final Element push) {
        final String instance = push.getAttribute("instance");
        final String application = push.getAttribute("application");
        if (Strings.isNullOrEmpty(instance) || Strings.isNullOrEmpty(application)) {
            return false;
        }
        final String content = push.getContent();
        final byte[] payload;
        if (Strings.isNullOrEmpty(content)) {
            payload = new byte[0];
        } else if (BaseEncoding.base64().canDecode(content)) {
            payload = BaseEncoding.base64().decode(content);
        } else {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid() + ": received invalid unified push payload");
            return false;
        }
        final Optional<UnifiedPushDatabase.PushTarget> pushTarget =
                getPushTarget(account, transport, application, instance);
        if (pushTarget.isPresent()) {
            final UnifiedPushDatabase.PushTarget target = pushTarget.get();
            // TODO check if app is still installed?
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": broadcasting a "
                            + payload.length
                            + " bytes push message to "
                            + target.application);
            broadcastPushMessage(target, payload);
            return true;
        } else {
            Log.d(Config.LOGTAG, "could not find application for push");
            return false;
        }
    }

    public Optional<Transport> getTransport() {
        final SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(service.getApplicationContext());
        final String accountPreference =
                sharedPreferences.getString(UnifiedPushDistributor.PREFERENCE_ACCOUNT, "none");
        final String pushServerPreference =
                sharedPreferences.getString(
                        UnifiedPushDistributor.PREFERENCE_PUSH_SERVER,
                        service.getString(R.string.default_push_server));
        if (Strings.isNullOrEmpty(accountPreference)
                || "none".equalsIgnoreCase(accountPreference)
                || Strings.nullToEmpty(pushServerPreference).trim().isEmpty()) {
            return Optional.absent();
        }
        final Jid transport;
        final Jid jid;
        try {
            transport = Jid.ofEscaped(Strings.nullToEmpty(pushServerPreference).trim());
            jid = Jid.ofEscaped(Strings.nullToEmpty(accountPreference).trim());
        } catch (final IllegalArgumentException e) {
            return Optional.absent();
        }
        final Account account = service.findAccountByJid(jid);
        if (account == null) {
            return Optional.absent();
        }
        return Optional.of(new Transport(account, transport));
    }

    private Optional<UnifiedPushDatabase.PushTarget> getPushTarget(
            final Account account,
            final Jid transport,
            final String application,
            final String instance) {
        if (transport == null || application == null || instance == null) {
            return Optional.absent();
        }
        final String uuid = account.getUuid();
        final List<UnifiedPushDatabase.PushTarget> pushTargets =
                UnifiedPushDatabase.getInstance(service)
                        .getPushTargets(uuid, transport.toEscapedString());
        return Iterables.tryFind(
                pushTargets,
                pt ->
                        UnifiedPushDistributor.hash(uuid, pt.application).equals(application)
                                && UnifiedPushDistributor.hash(uuid, pt.instance).equals(instance));
    }

    private void broadcastPushMessage(
            final UnifiedPushDatabase.PushTarget target, final byte[] payload) {
        final Intent updateIntent = new Intent(UnifiedPushDistributor.ACTION_MESSAGE);
        updateIntent.setPackage(target.application);
        updateIntent.putExtra("token", target.instance);
        updateIntent.putExtra("bytesMessage", payload);
        updateIntent.putExtra("message", new String(payload, StandardCharsets.UTF_8));
        final var distributorVerificationIntent = new Intent();
        distributorVerificationIntent.setPackage(service.getPackageName());
        final var pendingIntent =
                PendingIntent.getBroadcast(
                        service, 0, distributorVerificationIntent, PendingIntent.FLAG_IMMUTABLE);
        updateIntent.putExtra("distributor", pendingIntent);
        service.sendBroadcast(updateIntent);
    }

    private void broadcastEndpoint(
            final String instance, final UnifiedPushDatabase.ApplicationEndpoint endpoint) {
        Log.d(Config.LOGTAG, "broadcasting endpoint to " + endpoint.application);
        final Intent updateIntent = endpointIntent(instance, endpoint);
        service.sendBroadcast(updateIntent);
    }

    private Intent endpointIntent(final String instance, final UnifiedPushDatabase.ApplicationEndpoint endpoint) {
        final Intent intent = new Intent(UnifiedPushDistributor.ACTION_NEW_ENDPOINT);
        intent.setPackage(endpoint.application);
        intent.putExtra("token", instance);
        intent.putExtra("endpoint", endpoint.endpoint);
        final var distributorVerificationIntent = new Intent();
        distributorVerificationIntent.setPackage(service.getPackageName());
        final var pendingIntent =
                PendingIntent.getBroadcast(
                        service, 0, distributorVerificationIntent, PendingIntent.FLAG_IMMUTABLE);
        intent.putExtra("distributor", pendingIntent);
        return intent;
    }

    private Intent unregisteredIntent(final UnifiedPushDatabase.PushTarget pushTarget) {
        final Intent intent = new Intent(UnifiedPushDistributor.ACTION_UNREGISTERED);
        intent.setPackage(pushTarget.application);
        intent.putExtra("token", pushTarget.instance);
        final var distributorVerificationIntent = new Intent();
        distributorVerificationIntent.setPackage(service.getPackageName());
        final var pendingIntent =
                PendingIntent.getBroadcast(
                        service, 0, distributorVerificationIntent, PendingIntent.FLAG_IMMUTABLE);
        intent.putExtra("distributor", pendingIntent);
        return intent;
    }

    public void rebroadcastEndpoint(final Messenger messenger, final String instance, final Transport transport) {
        final UnifiedPushDatabase unifiedPushDatabase = UnifiedPushDatabase.getInstance(service);
        final UnifiedPushDatabase.ApplicationEndpoint endpoint =
                unifiedPushDatabase.getEndpoint(
                        transport.account.getUuid(),
                        transport.transport.toEscapedString(),
                        instance);
        if (endpoint != null) {
            sendEndpoint(messenger, instance, endpoint);
        }
    }

    public static class Transport {
        public final Account account;
        public final Jid transport;

        public Transport(Account account, Jid transport) {
            this.account = account;
            this.transport = transport;
        }
    }

    public static class PushTargetMessenger {
        private final UnifiedPushDatabase.PushTarget pushTarget;
        public final Messenger messenger;

        public PushTargetMessenger(UnifiedPushDatabase.PushTarget pushTarget, Messenger messenger) {
            this.pushTarget = pushTarget;
            this.messenger = messenger;
        }
    }
}
