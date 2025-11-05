package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.android.AbstractPhoneContact;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ReplacingSerialSingleThreadExecutor;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.roster.Item;
import im.conversations.android.xmpp.model.roster.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RosterManager extends AbstractManager implements Roster {

    private final ReplacingSerialSingleThreadExecutor dbExecutor =
            new ReplacingSerialSingleThreadExecutor(RosterManager.class.getName());

    private final Map<Jid, Contact> contacts = new HashMap<>();
    private String version;

    private final XmppConnectionService service;

    public RosterManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        this.version = getAccount().getRosterVersion();
        ;
        this.service = service;
    }

    public void request() {
        final var iq = new Iq(Iq.Type.GET);
        final var query = iq.addExtension(new Query());
        final var version = this.version;
        if (version != null) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid() + ": requesting roster version " + version);
            query.setVersion(version);
        } else {
            Log.d(Config.LOGTAG, getAccount().getJid().asBareJid() + " requesting roster");
        }
        final var future = connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Iq result) {
                        final var query = result.getExtension(Query.class);
                        if (query == null) {
                            // No query in result means further modifications are sent via pushes
                            return;
                        }
                        final var version = query.getVersion();
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": received full roster (version="
                                        + version
                                        + ")");
                        final var items = query.getItems();
                        // In a roster result (Section 2.1.4), the client MUST ignore values of the
                        // 'subscription'
                        // attribute other than "none", "to", "from", or "both".
                        final var validItems =
                                Collections2.filter(
                                        items,
                                        i ->
                                                Item.RESULT_SUBSCRIPTIONS.contains(
                                                                i.getSubscription())
                                                        && Objects.nonNull(i.getJid()));

                        setRosterItems(version, validItems);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not fetch roster",
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void setRosterItems(final String version, final Collection<Item> items) {
        synchronized (this.contacts) {
            markAllAsNotInRoster();
            for (final var item : items) {
                processRosterItem(item);
            }
            this.version = version;
        }
        this.triggerUiUpdates();
        this.writeToDatabaseAsync();
    }

    private void modifyRosterItems(final String version, final Collection<Item> items) {
        synchronized (this.contacts) {
            for (final var item : items) {
                processRosterItem(item);
            }
            this.version = version;
        }
        this.triggerUiUpdates();
        this.writeToDatabaseAsync();
    }

    private void triggerUiUpdates() {
        this.service.updateConversationUi();
        this.service.updateRosterUi();
        this.service.getShortcutService().refresh();
    }

    public void push(final Iq packet) {
        if (connection.fromServer(packet)) {
            final var query = packet.getExtension(Query.class);
            final var version = query.getVersion();
            modifyRosterItems(version, query.getItems());
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid() + ": received roster push (version=" + version + ")");
        } else {
            connection.sendErrorFor(packet, new Condition.Forbidden());
        }
    }

    private void processRosterItem(final Item item) {
        // this is verbatim the original code from IqParser.
        // TODO there are likely better ways to handle roster management
        final Jid jid = Jid.Invalid.getNullForInvalid(item.getAttributeAsJid("jid"));
        if (jid == null) {
            return;
        }
        final var name = item.getItemName();
        final var subscription = item.getSubscription();
        // getContactInternal is not synchronized because all access to processRosterItem is
        final var contact = getContactInternal(jid);
        boolean bothPre =
                contact.getOption(Contact.Options.TO) && contact.getOption(Contact.Options.FROM);
        if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
            contact.setServerName(name);
            // TODO use item.getGroups()
            contact.parseGroupsFromElement(item);
        }
        if (subscription == Item.Subscription.REMOVE) {
            contact.resetOption(Contact.Options.IN_ROSTER);
            contact.resetOption(Contact.Options.DIRTY_DELETE);
            contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
        } else {
            contact.setOption(Contact.Options.IN_ROSTER);
            contact.resetOption(Contact.Options.DIRTY_PUSH);
            // TODO use subscription; and set asking separately
            contact.parseSubscriptionFromElement(item);
        }
        boolean both =
                contact.getOption(Contact.Options.TO) && contact.getOption(Contact.Options.FROM);
        if ((both != bothPre) && both) {
            final var account = getAccount();
            Log.d(
                    Config.LOGTAG,
                    account.getJid().asBareJid()
                            + ": gained mutual presence subscription with "
                            + contact.getAddress());
            final var axolotlService = account.getAxolotlService();
            if (axolotlService != null) {
                axolotlService.clearErrorsInFetchStatusMap(contact.getAddress());
            }
        }
        service.getAvatarService().clear(contact);
    }

    @Override
    @NonNull
    public Contact getContact(@NonNull final Jid jid) {
        synchronized (this.contacts) {
            return this.getContactInternal(jid);
        }
    }

    @NonNull
    public Contact getContactInternal(@NonNull final Jid jid) {
        final var existing = this.contacts.get(jid.asBareJid());
        if (existing != null) {
            return existing;
        }
        final var contact = new Contact(jid.asBareJid());
        contact.setAccount(getAccount());
        this.contacts.put(jid.asBareJid(), contact);
        return contact;
    }

    @Override
    @Nullable
    public Contact getContactFromContactList(@NonNull final Jid jid) {
        synchronized (this.contacts) {
            final var contact = this.contacts.get(jid.asBareJid());
            if (contact != null && contact.showInContactList()) {
                return contact;
            } else {
                return null;
            }
        }
    }

    @Override
    public List<Contact> getContacts() {
        synchronized (this.contacts) {
            return ImmutableList.copyOf(this.contacts.values());
        }
    }

    @Override
    public ImmutableList<Contact> getWithSystemAccounts(
            final Class<? extends AbstractPhoneContact> clazz) {
        final int option = Contact.getOption(clazz);
        synchronized (this.contacts) {
            return ImmutableList.copyOf(
                    Collections2.filter(this.contacts.values(), c -> c.getOption(option)));
        }
    }

    private void markAllAsNotInRoster() {
        for (final var contact : this.contacts.values()) {
            contact.resetOption(Contact.Options.IN_ROSTER);
        }
    }

    public void restore() {
        synchronized (this.contacts) {
            this.contacts.clear();
            this.contacts.putAll(getDatabase().readRoster(getAccount()));
        }
    }

    public void writeToDatabaseAsync() {
        this.dbExecutor.execute(this::writeToDatabase);
    }

    public void writeToDatabase() {
        final var account = getAccount();
        final List<Contact> contacts;
        final String version;
        synchronized (this.contacts) {
            contacts = ImmutableList.copyOf(this.contacts.values());
            version = this.version;
        }
        getDatabase().writeRoster(account, version, contacts);
    }

    public void syncDirtyContacts() {
        synchronized (this.contacts) {
            for (final var contact : this.contacts.values()) {
                if (contact.getOption(Contact.Options.DIRTY_PUSH)) {
                    addRosterItem(contact, null);
                }
                if (contact.getOption(Contact.Options.DIRTY_DELETE)) {
                    deleteRosterItem(contact);
                }
            }
        }
    }

    public void addRosterItem(final Contact contact, final String preAuth) {
        final var address = contact.getAddress().asBareJid();
        contact.resetOption(Contact.Options.DIRTY_DELETE);
        contact.setOption(Contact.Options.DIRTY_PUSH);
        // sync the 'dirty push' flag to disk in case we are offline
        this.writeToDatabaseAsync();
        final boolean ask = contact.getOption(Contact.Options.ASKING);
        final boolean sendUpdates =
                contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)
                        && contact.getOption(Contact.Options.PREEMPTIVE_GRANT);
        final Iq iq = new Iq(Iq.Type.SET);
        final var query = iq.addExtension(new Query());
        final var item = query.addExtension(new Item());
        item.setJid(address);
        final var serverName = contact.getServerName();
        if (serverName != null) {
            item.setItemName(serverName);
        }
        item.setGroups(contact.getGroups());
        final var future = this.connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(Iq result) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": pushed roster item "
                                        + address);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not push roster item "
                                        + address,
                                t);
                    }
                },
                MoreExecutors.directExecutor());
        if (sendUpdates) {
            getManager(PresenceManager.class).subscribed(contact.getAddress().asBareJid());
        }
        if (ask) {
            getManager(PresenceManager.class).subscribe(contact.getAddress().asBareJid(), preAuth);
        }
    }

    public void deleteRosterItem(final Contact contact) {
        final var address = contact.getAddress().asBareJid();
        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
        contact.resetOption(Contact.Options.DIRTY_PUSH);
        contact.setOption(Contact.Options.DIRTY_DELETE);
        this.writeToDatabaseAsync();
        final Iq iq = new Iq(Iq.Type.SET);
        final var query = iq.addExtension(new Query());
        final var item = query.addExtension(new Item());
        item.setJid(address);
        item.setSubscription(Item.Subscription.REMOVE);
        final var future = this.connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(final Iq result) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": removed roster item "
                                        + address);
                    }

                    @Override
                    public void onFailure(final @NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not remove roster item "
                                        + address,
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }
}
