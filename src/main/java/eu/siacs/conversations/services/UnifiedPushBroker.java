package eu.siacs.conversations.services;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.BaseEncoding;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
                renewUnifiedEndpoint(transportOptional.get());
            }
        }
    }

    private void sendDirectedPresence(final Account account, Jid to) {
        final PresencePacket presence = new PresencePacket();
        presence.setTo(to);
        service.sendPresencePacket(account, presence);
    }

    public Optional<Transport> renewUnifiedPushEndpoints() {
        final Optional<Transport> transportOptional = getTransport();
        if (transportOptional.isPresent()) {
            final Transport transport = transportOptional.get();
            if (transport.account.isEnabled()) {
                renewUnifiedEndpoint(transportOptional.get());
            } else {
                Log.d(Config.LOGTAG, "skipping UnifiedPush endpoint renewal. Account is disabled");
            }
        } else {
            Log.d(Config.LOGTAG, "skipping UnifiedPush endpoint renewal. No transport selected");
        }
        return transportOptional;
    }

    private void renewUnifiedEndpoint(final Transport transport) {
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
            final String hashedApplication =
                    UnifiedPushDistributor.hash(account.getUuid(), renewal.application);
            final String hashedInstance =
                    UnifiedPushDistributor.hash(account.getUuid(), renewal.instance);
            final IqPacket registration = new IqPacket(IqPacket.TYPE.SET);
            registration.setTo(transport.transport);
            final Element register = registration.addChild("register", Namespace.UNIFIED_PUSH);
            register.setAttribute("application", hashedApplication);
            register.setAttribute("instance", hashedInstance);
            this.service.sendIqPacket(
                    account,
                    registration,
                    (a, response) -> processRegistration(transport, renewal, response));
        }
    }

    private void processRegistration(
            final Transport transport,
            final UnifiedPushDatabase.PushTarget renewal,
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
            renewUnifiedPushEndpoint(transport, renewal, endpoint, expiration);
        } else {
            Log.d(Config.LOGTAG, "could not register UP endpoint " + response.getErrorCondition());
        }
    }

    private void renewUnifiedPushEndpoint(
            final Transport transport,
            final UnifiedPushDatabase.PushTarget renewal,
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
            broadcastEndpoint(
                    renewal.instance,
                    new UnifiedPushDatabase.ApplicationEndpoint(renewal.application, endpoint));
        }
    }

    public boolean reconfigurePushDistributor() {
        final boolean enabled = getTransport().isPresent();
        setUnifiedPushDistributorEnabled(enabled);
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
        service.sendBroadcast(updateIntent);
    }

    private void broadcastEndpoint(
            final String instance, final UnifiedPushDatabase.ApplicationEndpoint endpoint) {
        Log.d(Config.LOGTAG, "broadcasting endpoint to " + endpoint.application);
        final Intent updateIntent = new Intent(UnifiedPushDistributor.ACTION_NEW_ENDPOINT);
        updateIntent.setPackage(endpoint.application);
        updateIntent.putExtra("token", instance);
        updateIntent.putExtra("endpoint", endpoint.endpoint);
        service.sendBroadcast(updateIntent);
    }

    public void rebroadcastEndpoint(final String instance, final Transport transport) {
        final UnifiedPushDatabase unifiedPushDatabase = UnifiedPushDatabase.getInstance(service);
        final UnifiedPushDatabase.ApplicationEndpoint endpoint =
                unifiedPushDatabase.getEndpoint(
                        transport.account.getUuid(),
                        transport.transport.toEscapedString(),
                        instance);
        if (endpoint != null) {
            broadcastEndpoint(instance, endpoint);
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
}
