package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.Device;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.Entity;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.capabilties.Capabilities;
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities;
import im.conversations.android.xmpp.model.idle.Idle;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pars.PreAuth;
import im.conversations.android.xmpp.model.pgp.Signed;
import im.conversations.android.xmpp.model.stanza.Presence;
import im.conversations.android.xmpp.model.vcard.update.VCardUpdate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.openintents.openpgp.util.OpenPgpUtils;

public class PresenceManager extends AbstractManager {

    private final XmppConnectionService service;
    private final AppSettings appSettings;

    private final Multimap<Jid, Presence> presences = ArrayListMultimap.create();

    private final Map<EntityCapabilities.Hash, ServiceDescription> serviceDescriptions =
            new HashMap<>();

    public PresenceManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.appSettings = new AppSettings(service.getApplicationContext());
        this.service = service;
    }

    public void handlePresence(final Presence presence) {
        final var from = presence.getFrom();
        final var type = presence.getType();
        if (from == null || from.equals(getAccount().getJid())) {
            return;
        }
        if (from.isBareJid() && getManager(MultiUserChatManager.class).isMuc(from.asBareJid())) {
            // the old vCard updates will end up here
            return;
        }
        if (type == null) {
            this.handleAvailablePresence(presence);
        } else if (type == Presence.Type.UNAVAILABLE) {
            this.handleUnavailablePresence(presence);
        } else if (type == Presence.Type.SUBSCRIBE) {
            this.handleSubscribePresence(presence);
        } else {
            Log.e(Config.LOGTAG, getAccount().getJid().asBareJid() + ": not handling " + presence);
        }
        this.service.updateRosterUi();
    }

    private void handleAvailablePresence(final Presence presence) {
        final var from = presence.getFrom();
        final var account = getAccount();
        final var contact = getManager(RosterManager.class).getContact(from);
        final int sizeBefore = contact.getPresences().size();

        synchronized (this.presences) {
            remove(this.presences, from);
            this.presences.put(from.asBareJid(), presence);
        }

        final var nodeHash = presence.getCapabilities();
        if (nodeHash != null) {
            final var discoFuture =
                    this.getManager(DiscoManager.class)
                            .infoOrCache(Entity.presence(from), nodeHash.node, nodeHash.hash);

            awaitDiscoFuture(contact, discoFuture);
        }

        final var interaction = contact.getLastUserInteraction();
        Log.d(Config.LOGTAG, "interaction for " + contact.getAddress() + ": " + interaction);

        final PgpEngine pgp = this.service.getPgpEngine();
        final Element x = presence.getExtension(Signed.class);
        if (pgp != null && x != null) {
            final String status = presence.getStatus();
            final long keyId = pgp.fetchKeyId(account, status, x.getContent());
            if (keyId != 0 && contact.setPgpKeyId(keyId)) {
                Log.d(
                        Config.LOGTAG,
                        account.getJid().asBareJid()
                                + ": found OpenPGP key id for "
                                + contact.getAddress()
                                + " "
                                + OpenPgpUtils.convertKeyIdToHex(keyId));
                this.connection.getManager(RosterManager.class).writeToDatabaseAsync();
            }
        }
        final boolean online = sizeBefore < contact.getPresences().size();
        this.service.onContactStatusChanged.onContactStatusChanged(contact, online);
    }

    private void handleUnavailablePresence(final Presence packet) {
        final var account = getAccount().getJid().asBareJid();
        final var from = packet.getFrom();
        if (from == null || from.equals(account.getDomain()) || from.equals(account)) {
            // Snikket sends unavailable presence from the server domain. We ignore this mostly in
            // order to avoid executing DiscoManager.clear() which would have caused server disco
            // features to go away
            // the operation on the 'Contact' object will also be ignored but those are irrelevant
            // anyway.
            Log.d(Config.LOGTAG, "ignoring unavailable presence from " + from);
            final var vCardUpdate = packet.getExtension(VCardUpdate.class);
            if (vCardUpdate != null && account.getDomain().equals(from)) {
                // Snikket special feature
                getManager(AvatarManager.class).handleVCardUpdate(from, vCardUpdate);
            }
            return;
        }
        final var contact = this.getManager(RosterManager.class).getContact(from);

        // the clear function will be a no-op in case the unavailable presence is coming from an
        // item listed in disco#item. why that would be the case who knows but we are also
        // deliberately ignoring presence from the server
        getManager(DiscoManager.class).clear(from);

        synchronized (this.presences) {
            if (from.isBareJid()) {
                this.presences.removeAll(from.asBareJid());
            } else {
                remove(this.presences, from);
            }
        }
        this.service.onContactStatusChanged.onContactStatusChanged(contact, false);
    }

    private void handleSubscribePresence(final Presence packet) {
        final var from = packet.getFrom();
        final var account = getAccount();
        final var contact = this.getManager(RosterManager.class).getContact(from);
        if (contact.isBlocked()) {
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": ignoring 'subscribe' presence from blocked "
                            + from);
            return;
        }
        final var nick = packet.getExtension(Nick.class);
        if (nick != null && contact.setPresenceName(nick.getContent())) {
            getManager(RosterManager.class).writeToDatabaseAsync();
            this.service.getAvatarService().clear(contact);
        }
        if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
            connection
                    .getManager(PresenceManager.class)
                    .subscribed(contact.getAddress().asBareJid());
        } else {
            contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
            final Conversation conversation =
                    this.service.findOrCreateConversation(
                            account, contact.getAddress().asBareJid(), false, false);
            final String statusMessage = packet.findChildContent("status");
            if (statusMessage != null
                    && !statusMessage.isEmpty()
                    && conversation.countMessages() == 0) {
                conversation.add(
                        new Message(
                                conversation,
                                statusMessage,
                                Message.ENCRYPTION_NONE,
                                Message.STATUS_RECEIVED));
            }
        }
    }

    private void awaitDiscoFuture(final Contact contact, ListenableFuture<Void> discoFuture) {
        Futures.addCallback(
                discoFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (contact.refreshRtpCapability()) {
                            getManager(RosterManager.class).writeToDatabaseAsync();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            return;
                        }
                        Log.d(
                                Config.LOGTAG,
                                "could not retrieve disco from " + contact.getAddress(),
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void clear() {
        synchronized (this.presences) {
            for (final var presence : this.presences.values()) {
                getManager(DiscoManager.class).clear(presence.getFrom());
            }
            this.presences.clear();
        }
    }

    public List<Presence> getPresences(final Jid address) {
        synchronized (this.presences) {
            return ImmutableList.copyOf(this.presences.get(address));
        }
    }

    public void subscribe(final Jid address) {
        subscribe(address, null);
    }

    public void subscribe(final Jid address, final String preAuth) {
        final var presence = new Presence(Presence.Type.SUBSCRIBE);
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
        final var presence = new Presence(Presence.Type.UNSUBSCRIBE);
        presence.setTo(address);
        this.connection.sendPresencePacket(presence);
    }

    public void unsubscribed(final Jid address) {
        final var presence = new Presence(Presence.Type.UNSUBSCRIBED);
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
            presence.addExtension(new Idle(Instant.ofEpochMilli(since)));
        }
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

    public ServiceDescription getCachedServiceDescription(final EntityCapabilities.Hash hash) {
        return this.serviceDescriptions.get(hash);
    }

    private static void remove(final Multimap<Jid, Presence> map, final Jid address) {
        final var existing = map.get(address.asBareJid());
        final var existingWithAddressRemoved =
                ImmutableList.copyOf(
                        Collections2.filter(existing, p -> !address.equals(p.getFrom())));
        map.replaceValues(address.asBareJid(), existingWithAddressRemoved);
    }
}
