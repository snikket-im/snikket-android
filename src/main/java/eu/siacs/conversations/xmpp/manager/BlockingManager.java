package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.blocking.Block;
import im.conversations.android.xmpp.model.blocking.Blocklist;
import im.conversations.android.xmpp.model.blocking.Item;
import im.conversations.android.xmpp.model.blocking.Unblock;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.reporting.Report;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.unique.StanzaId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BlockingManager extends AbstractManager {

    private final XmppConnectionService service;

    private final HashSet<Jid> blocklist = new HashSet<>();

    public BlockingManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service, connection);
        // TODO find a way to get rid of XmppConnectionService and use context instead
        this.service = service;
    }

    public void request() {
        final var future = this.connection.sendIqPacket(new Iq(Iq.Type.GET, new Blocklist()));
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(final Iq result) {
                        final var blocklist = result.getExtension(Blocklist.class);
                        if (blocklist == null) {
                            Log.d(
                                    Config.LOGTAG,
                                    getAccount().getJid().asBareJid()
                                            + ": invalid blocklist response");
                            return;
                        }
                        final var addresses = itemsAsAddresses(blocklist.getItems());
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": discovered blocklist with "
                                        + addresses.size()
                                        + " items");
                        setBlocklist(addresses);
                        removeBlockedConversations(addresses);
                        service.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
                    }

                    @Override
                    public void onFailure(@NonNull final Throwable throwable) {
                        Log.w(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not retrieve blocklist",
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
    }

    public void pushBlock(final Iq request) {
        if (connection.fromServer(request)) {
            final var block = request.getExtension(Block.class);
            final var addresses = itemsAsAddresses(block.getItems());
            synchronized (this.blocklist) {
                this.blocklist.addAll(addresses);
            }
            this.removeBlockedConversations(addresses);
            this.service.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
            this.connection.sendResultFor(request);
        } else {
            this.connection.sendErrorFor(request, Error.Type.AUTH, new Condition.Forbidden());
        }
    }

    public void pushUnblock(final Iq request) {
        if (connection.fromServer(request)) {
            final var unblock = request.getExtension(Unblock.class);
            final var address = itemsAsAddresses(unblock.getItems());
            synchronized (this.blocklist) {
                this.blocklist.removeAll(address);
            }
            this.service.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
            this.connection.sendResultFor(request);
        } else {
            this.connection.sendErrorFor(request, Error.Type.AUTH, new Condition.Forbidden());
        }
    }

    private void removeBlockedConversations(final Collection<Jid> addresses) {
        var removed = false;
        for (final Jid address : addresses) {
            removed |= service.removeBlockedConversations(getAccount(), address);
        }
        if (removed) {
            service.updateConversationUi();
        }
    }

    public ImmutableSet<Jid> getBlocklist() {
        synchronized (this.blocklist) {
            return ImmutableSet.copyOf(this.blocklist);
        }
    }

    private void setBlocklist(final Collection<Jid> addresses) {
        synchronized (this.blocklist) {
            this.blocklist.clear();
            this.blocklist.addAll(addresses);
        }
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasServerFeature(Namespace.BLOCKING);
    }

    private static Set<Jid> itemsAsAddresses(final Collection<Item> items) {
        final var builder = new ImmutableSet.Builder<Jid>();
        for (final var item : items) {
            final var jid = Jid.Invalid.getNullForInvalid(item.getJid());
            if (jid == null) {
                continue;
            }
            builder.add(jid);
        }
        return builder.build();
    }

    public boolean block(
            @NonNull final Blockable blockable,
            final boolean reportSpam,
            @Nullable final String serverMsgId) {
        final var address = blockable.getBlockedJid();
        final var iq = new Iq(Iq.Type.SET);
        final var block = iq.addExtension(new Block());
        final var item = block.addExtension(new Item());
        item.setJid(address);
        if (reportSpam) {
            final var report = item.addExtension(new Report());
            report.setReason(Namespace.REPORTING_REASON_SPAM);
            if (serverMsgId != null) {
                // XEP has a 'by' attribute that is the same as reported jid but that doesn't make
                // sense this the 'by' attribute in the stanza-id refers to the arriving entity
                // (usually the account or the MUC)
                report.addExtension(new StanzaId(serverMsgId));
            }
        }
        final var future = this.connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Iq result) {
                        synchronized (blocklist) {
                            blocklist.add(address);
                        }
                        service.updateBlocklistUi(OnUpdateBlocklist.Status.BLOCKED);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable throwable) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid() + ": could not block " + address,
                                throwable);
                    }
                },
                MoreExecutors.directExecutor());
        if (address.isFullJid()) {
            return false;
        } else if (service.removeBlockedConversations(getAccount(), address)) {
            service.updateConversationUi();
            return true;
        } else {
            return false;
        }
    }

    public void unblock(@NonNull final Blockable blockable) {
        final var address = blockable.getBlockedJid();
        final var iq = new Iq(Iq.Type.SET);
        final var unblock = iq.addExtension(new Unblock());
        final var item = unblock.addExtension(new Item());
        item.setJid(address);
        final var future = this.connection.sendIqPacket(iq);
        Futures.addCallback(
                future,
                new FutureCallback<Iq>() {
                    @Override
                    public void onSuccess(Iq result) {
                        synchronized (blocklist) {
                            blocklist.remove(address);
                        }
                        service.updateBlocklistUi(OnUpdateBlocklist.Status.UNBLOCKED);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.d(
                                Config.LOGTAG,
                                getAccount().getJid().asBareJid()
                                        + ": could not unblock "
                                        + address,
                                t);
                    }
                },
                MoreExecutors.directExecutor());
    }
}
