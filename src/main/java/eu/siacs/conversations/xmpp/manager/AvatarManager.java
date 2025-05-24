package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.pep.Avatar;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.avatar.Data;
import im.conversations.android.xmpp.model.avatar.Info;
import im.conversations.android.xmpp.model.avatar.Metadata;
import im.conversations.android.xmpp.model.pubsub.Items;

public class AvatarManager extends AbstractManager {

    private final XmppConnectionService service;

    public AvatarManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public ListenableFuture<byte[]> fetch(final Jid address, final String itemId) {
        final var future = getManager(PubSubManager.class).fetchItem(address, itemId, Data.class);
        return Futures.transform(future, ByteContent::asBytes, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> fetchAndStore(final Avatar avatar) {
        final var future = fetch(avatar.owner, avatar.sha1sum);
        return Futures.transform(
                future,
                data -> {
                    avatar.image = BaseEncoding.base64().encode(data);
                    if (service.getFileBackend().save(avatar)) {
                        setPepAvatar(avatar);
                        return null;
                    } else {
                        throw new IllegalStateException("Could not store avatar");
                    }
                },
                MoreExecutors.directExecutor());
    }

    private void setPepAvatar(final Avatar avatar) {
        final var account = getAccount();
        if (account.getJid().asBareJid().equals(avatar.owner)) {
            if (account.setAvatar(avatar.getFilename())) {
                getDatabase().updateAccount(account);
            }
            this.service.getAvatarService().clear(account);
            this.service.updateConversationUi();
            this.service.updateAccountUi();
        } else {
            final Contact contact = account.getRoster().getContact(avatar.owner);
            contact.setAvatar(avatar);
            account.getXmppConnection().getManager(RosterManager.class).writeToDatabaseAsync();
            this.service.getAvatarService().clear(contact);
            this.service.updateConversationUi();
            this.service.updateRosterUi();
        }
    }

    public void handleItems(final Jid from, final Items items) {
        final var account = getAccount();
        // TODO support retract
        final var entry = items.getFirstItemWithId(Metadata.class);
        final var avatar =
                entry == null ? null : Avatar.parseMetadata(entry.getKey(), entry.getValue());
        if (avatar == null) {
            Log.d(Config.LOGTAG, "could not parse avatar metadata from " + from);
            return;
        }
        avatar.owner = from.asBareJid();
        if (service.getFileBackend().isAvatarCached(avatar)) {
            if (account.getJid().asBareJid().equals(from)) {
                if (account.setAvatar(avatar.getFilename())) {
                    service.databaseBackend.updateAccount(account);
                    service.notifyAccountAvatarHasChanged(account);
                }
                service.getAvatarService().clear(account);
                service.updateConversationUi();
                service.updateAccountUi();
            } else {
                final Contact contact = account.getRoster().getContact(from);
                if (contact.setAvatar(avatar)) {
                    connection.getManager(RosterManager.class).writeToDatabaseAsync();
                    service.getAvatarService().clear(contact);
                    service.updateConversationUi();
                    service.updateRosterUi();
                }
            }
        } else if (service.isDataSaverDisabled()) {
            final var future = this.fetchAndStore(avatar);
            Futures.addCallback(
                    future,
                    new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            Log.d(
                                    Config.LOGTAG,
                                    account.getJid().asBareJid()
                                            + ": successfully fetched pep avatar for "
                                            + avatar.owner);
                        }

                        @Override
                        public void onFailure(@NonNull Throwable t) {
                            Log.d(Config.LOGTAG, "could not fetch avatar", t);
                        }
                    },
                    MoreExecutors.directExecutor());
        }
    }

    public void handleDelete(final Jid from) {
        final var account = getAccount();
        final boolean isAccount = account.getJid().asBareJid().equals(from);
        if (isAccount) {
            account.setAvatar(null);
            getDatabase().updateAccount(account);
            service.getAvatarService().clear(account);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted avatar metadata node");
        }
    }

    public ListenableFuture<Void> publish(final Avatar avatar, final boolean open) {
        final NodeConfiguration configuration =
                open ? NodeConfiguration.OPEN : NodeConfiguration.PRESENCE;
        final var avatarData = new Data();
        avatarData.setContent(avatar.getImageAsBytes());
        final var future =
                getManager(PepManager.class).publish(avatarData, avatar.sha1sum, configuration);
        return Futures.transformAsync(
                future,
                v -> {
                    final var id = avatar.sha1sum;
                    final var metadata = new Metadata();
                    final var info = metadata.addExtension(new Info());
                    info.setBytes(avatar.size);
                    info.setId(avatar.sha1sum);
                    info.setHeight(avatar.height);
                    info.setWidth(avatar.width);
                    info.setType(avatar.type);
                    return getManager(PepManager.class).publish(metadata, id, configuration);
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasPepToVCardConversion() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.AVATAR_CONVERSION);
    }

    public ListenableFuture<Void> delete() {
        final var pepManager = getManager(PepManager.class);
        final var deleteMetaDataFuture = pepManager.delete(Namespace.AVATAR_METADATA);
        final var deleteDataFuture = pepManager.delete(Namespace.AVATAR_DATA);
        return Futures.transform(
                Futures.allAsList(deleteDataFuture, deleteMetaDataFuture),
                vs -> null,
                MoreExecutors.directExecutor());
    }
}
