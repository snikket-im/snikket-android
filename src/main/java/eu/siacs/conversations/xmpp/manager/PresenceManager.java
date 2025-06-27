package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import com.google.common.base.Strings;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.Device;
import eu.siacs.conversations.generator.AbstractGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.capabilties.Capabilities;
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pars.PreAuth;
import im.conversations.android.xmpp.model.pgp.Signed;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.HashMap;
import java.util.Map;

public class PresenceManager extends AbstractManager {

    private final XmppConnectionService service;
    private final AppSettings appSettings;

    private final Map<EntityCapabilities.Hash, ServiceDescription> serviceDescriptions =
            new HashMap<>();

    public PresenceManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.appSettings = new AppSettings(service.getApplicationContext());
        this.service = service;
    }

    public void subscribe(final Jid address) {
        subscribe(address, null);
    }

    public void subscribe(final Jid address, final String preAuth) {

        var presence = new Presence(Presence.Type.SUBSCRIBE);
        presence.setTo(address);

        final var displayName = getAccount().getDisplayName();
        if (!Strings.isNullOrEmpty(displayName)) {
            presence.addExtension(new Nick(displayName));
        }
        if (preAuth != null) {
            presence.addExtension(new PreAuth()).setToken(preAuth);
        }
        this.connection.sendPresencePacket(presence);
    }

    public void unsubscribe(final Jid address) {
        var presence = new Presence(Presence.Type.UNSUBSCRIBE);
        presence.setTo(address);
        this.connection.sendPresencePacket(presence);
    }

    public void unsubscribed(final Jid address) {
        var presence = new Presence(Presence.Type.UNSUBSCRIBED);
        presence.setTo(address);
        this.connection.sendPresencePacket(presence);
    }

    public void subscribed(final Jid address) {
        var presence = new Presence(Presence.Type.SUBSCRIBED);
        presence.setTo(address);
        this.connection.sendPresencePacket(presence);
    }

    public void available() {
        available(service.checkListeners() && appSettings.isBroadcastLastActivity());
    }

    public void available(final boolean withIdle) {
        final var account = connection.getAccount();
        final var serviceDiscoveryFeatures = getManager(DiscoManager.class).getServiceDescription();
        final var infoQuery = serviceDiscoveryFeatures.asInfoQuery();
        final var capsHash = EntityCapabilities.hash(infoQuery);
        final var caps2Hash = EntityCapabilities2.hash(infoQuery);
        serviceDescriptions.put(capsHash, serviceDiscoveryFeatures);
        serviceDescriptions.put(caps2Hash, serviceDiscoveryFeatures);
        final var capabilities = new Capabilities();
        capabilities.setHash(caps2Hash);
        final var legacyCapabilities = new LegacyCapabilities();
        legacyCapabilities.setNode(DiscoManager.CAPABILITY_NODE);
        legacyCapabilities.setHash(capsHash);
        final var presence = new Presence();
        presence.addExtension(capabilities);
        presence.addExtension(legacyCapabilities);
        final String pgpSignature = account.getPgpSignature();
        final String message = account.getPresenceStatusMessage();
        final Presence.Availability availability;
        if (appSettings.isUserManagedAvailability()) {
            availability = account.getPresenceStatus();
        } else {
            availability = getTargetPresence();
        }
        presence.setAvailability(availability);
        presence.setStatus(message);
        if (pgpSignature != null) {
            presence.addExtension(new Signed(pgpSignature));
        }

        final var lastActivity = service.getLastActivity();
        if (lastActivity > 0 && withIdle) {
            final long since =
                    Math.min(lastActivity, System.currentTimeMillis()); // don't send future dates
            presence.addChild("idle", Namespace.IDLE)
                    .setAttribute("since", AbstractGenerator.getTimestamp(since));
        }
        Log.d(Config.LOGTAG, "--> " + presence);
        connection.sendPresencePacket(presence);
    }

    public void unavailable() {
        var presence = new Presence(Presence.Type.UNAVAILABLE);
        this.connection.sendPresencePacket(presence);
    }

    public void available(final Jid to, final Extension... extensions) {
        available(to, null, extensions);
    }

    public void available(final Jid to, final String message, final Extension... extensions) {
        final var presence = new Presence();
        presence.setTo(to);
        presence.setStatus(message);
        for (final var extension : extensions) {
            presence.addExtension(extension);
        }
        connection.sendPresencePacket(presence);
    }

    public void unavailable(final Jid to) {
        final var presence = new Presence(Presence.Type.UNAVAILABLE);
        presence.setTo(to);
        connection.sendPresencePacket(presence);
    }

    private im.conversations.android.xmpp.model.stanza.Presence.Availability getTargetPresence() {
        final var device = new Device(context);
        if (appSettings.isDndOnSilentMode()
                && device.isPhoneSilenced(appSettings.isTreatVibrateAsSilent())) {
            return im.conversations.android.xmpp.model.stanza.Presence.Availability.DND;
        } else if (appSettings.isAwayWhenScreenLocked() && device.isScreenLocked()) {
            return im.conversations.android.xmpp.model.stanza.Presence.Availability.AWAY;
        } else {
            return im.conversations.android.xmpp.model.stanza.Presence.Availability.ONLINE;
        }
    }

    public Presence getPresence(final Presence.Availability availability, final boolean personal) {
        final var account = connection.getAccount();
        final var serviceDiscoveryFeatures = getManager(DiscoManager.class).getServiceDescription();
        final var infoQuery = serviceDiscoveryFeatures.asInfoQuery();
        final var capsHash = EntityCapabilities.hash(infoQuery);
        final var caps2Hash = EntityCapabilities2.hash(infoQuery);
        serviceDescriptions.put(capsHash, serviceDiscoveryFeatures);
        serviceDescriptions.put(caps2Hash, serviceDiscoveryFeatures);
        final var capabilities = new Capabilities();
        capabilities.setHash(caps2Hash);
        final var legacyCapabilities = new LegacyCapabilities();
        legacyCapabilities.setNode(DiscoManager.CAPABILITY_NODE);
        legacyCapabilities.setHash(capsHash);
        final var presence = new Presence();
        presence.addExtension(capabilities);
        presence.addExtension(legacyCapabilities);

        if (personal) {
            final String pgpSignature = account.getPgpSignature();
            final String message = account.getPresenceStatusMessage();
            presence.setAvailability(availability);
            presence.setStatus(message);
            if (pgpSignature != null) {
                final var signed = new Signed();
                signed.setContent(pgpSignature);
                presence.addExtension(new Signed());
            }
        }
        return presence;
    }

    public ServiceDescription getCachedServiceDescription(final EntityCapabilities.Hash hash) {
        return this.serviceDescriptions.get(hash);
    }
}
