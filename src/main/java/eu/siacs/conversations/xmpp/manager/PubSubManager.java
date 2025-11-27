package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.ExtensionFactory;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.PreconditionNotMetException;
import im.conversations.android.xmpp.PubSubErrorException;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.pubsub.Items;
import im.conversations.android.xmpp.model.pubsub.PubSub;
import im.conversations.android.xmpp.model.pubsub.Publish;
import im.conversations.android.xmpp.model.pubsub.PublishOptions;
import im.conversations.android.xmpp.model.pubsub.Retract;
import im.conversations.android.xmpp.model.pubsub.error.PubSubError;
import im.conversations.android.xmpp.model.pubsub.event.Delete;
import im.conversations.android.xmpp.model.pubsub.event.Event;
import im.conversations.android.xmpp.model.pubsub.event.Purge;
import im.conversations.android.xmpp.model.pubsub.owner.Configure;
import im.conversations.android.xmpp.model.pubsub.owner.PubSubOwner;
import im.conversations.android.xmpp.model.stanza.Iq;
import im.conversations.android.xmpp.model.stanza.Message;
import java.util.Map;

public class PubSubManager extends AbstractManager {

    private static final String SINGLETON_ITEM_ID = "current";

    public PubSubManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public void handleEvent(final Message message) {
        final var event = message.getExtension(Event.class);
        final var action = event.getAction();
        final var from = message.getFrom();

        if (from instanceof Jid.Invalid) {
            Log.d(
                    Config.LOGTAG,
                    getAccount().getJid().asBareJid() + ": ignoring event from invalid jid");
            return;
        }

        if (action instanceof Purge purge) {
            // purge is a deletion of all items in a node
            handlePurge(message, purge);
        } else if (action instanceof Items items) {
            // the items wrapper contains, new and updated items as well as retractions which are
            // deletions of individual items in a node
            handleItems(message, items);
        } else if (action instanceof Delete delete) {
            // delete is the deletion of the node itself
            handleDelete(message, delete);
        }
    }

    public <T extends Extension> ListenableFuture<Map<String, T>> fetchItems(
            final Jid address, final Class<T> clazz) {
        final var id = ExtensionFactory.id(clazz);
        if (id == null) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("%s is not a registered extension", clazz.getName())));
        }
        return fetchItems(address, id.namespace, clazz);
    }

    public <T extends Extension> ListenableFuture<Map<String, T>> fetchItems(
            final Jid address, final String node, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getItemMap(clazz);
                },
                MoreExecutors.directExecutor());
    }

    public <T extends Extension> ListenableFuture<T> fetchItem(
            final Jid address, final String itemId, final Class<T> clazz) {
        final var id = ExtensionFactory.id(clazz);
        if (id == null) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("%s is not a registered extension", clazz.getName())));
        }
        return fetchItem(address, id.namespace, itemId, clazz);
    }

    public <T extends Extension> ListenableFuture<T> fetchItem(
            final Jid address, final String node, final String itemId, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        final var item = itemsWrapper.addExtension(new PubSub.Item());
        item.setId(itemId);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getItemOrThrow(itemId, clazz);
                },
                MoreExecutors.directExecutor());
    }

    public <T extends Extension> ListenableFuture<T> fetchMostRecentItem(
            final Jid address, final Class<T> clazz) {
        final var id = ExtensionFactory.id(clazz);
        if (id == null) {
            return Futures.immediateFailedFuture(
                    new IllegalArgumentException(
                            String.format("%s is not a registered extension", clazz.getName())));
        }
        return fetchMostRecentItem(address, id.namespace, clazz);
    }

    public <T extends Extension> ListenableFuture<T> fetchMostRecentItem(
            final Jid address, final String node, final Class<T> clazz) {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(address);
        final var pubSub = request.addExtension(new PubSub());
        final var itemsWrapper = pubSub.addExtension(new PubSub.ItemsWrapper());
        itemsWrapper.setNode(node);
        itemsWrapper.setMaxItems(1);
        return Futures.transform(
                connection.sendIqPacket(request),
                response -> {
                    final var pubSubResponse = response.getExtension(PubSub.class);
                    if (pubSubResponse == null) {
                        throw new IllegalStateException();
                    }
                    final var items = pubSubResponse.getItems();
                    if (items == null) {
                        throw new IllegalStateException();
                    }
                    return items.getOnlyItem(clazz);
                },
                MoreExecutors.directExecutor());
    }

    private void handleItems(final Message message, final Items items) {
        final var from = message.getFrom();
        final var isFromBare = from == null || from.isBareJid();
        final var node = items.getNode();
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2.equals(node)) {
            getManager(NativeBookmarkManager.class).handleItems(items);
            return;
        }
        if (connection.fromAccount(message) && Namespace.BOOKMARKS.equals(node)) {
            getManager(LegacyBookmarkManager.class).handleItems(items);
            return;
        }
        if (connection.fromAccount(message) && Namespace.MDS_DISPLAYED.equals(node)) {
            getManager(MessageDisplayedSynchronizationManager.class).handleItems(items);
            return;
        }
        if (isFromBare && Namespace.AVATAR_METADATA.equals(node)) {
            getManager(AvatarManager.class).handleItems(from, items);
            return;
        }
        if (isFromBare && Namespace.NICK.equals(node)) {
            getManager(NickManager.class).handleItems(from, items);
            return;
        }
        if (isFromBare && Namespace.AXOLOTL_DEVICE_LIST.equals(node)) {
            getManager(AxolotlManager.class).handleItems(from, items);
        }
    }

    private void handlePurge(final Message message, final Purge purge) {
        final var from = message.getFrom();
        final var isFromBare = from == null || from.isBareJid();
        final var node = purge.getNode();
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2.equals(node)) {
            getManager(NativeBookmarkManager.class).handlePurge();
        }
        if (isFromBare && Namespace.AVATAR_METADATA.equals(node)) {
            // purge (delete all items in a node) is functionally equivalent to delete
            getManager(AvatarManager.class).handleDelete(from);
        }
    }

    private void handleDelete(final Message message, final Delete delete) {
        final var from = message.getFrom();
        final var isFromBare = from == null || from.isBareJid();
        final var node = delete.getNode();
        if (connection.fromAccount(message) && Namespace.BOOKMARKS2.equals(node)) {
            getManager(NativeBookmarkManager.class).handleDelete();
            return;
        }
        if (isFromBare && Namespace.AVATAR_METADATA.equals(node)) {
            getManager(AvatarManager.class).handleDelete(from);
            return;
        }
        if (isFromBare && Namespace.NICK.equals(node)) {
            getManager(NickManager.class).handleDelete(from);
        }
    }

    public ListenableFuture<Void> publishSingleton(
            Jid address, Extension item, final NodeConfiguration nodeConfiguration) {
        final var id = ExtensionFactory.id(item.getClass());
        return publish(address, item, SINGLETON_ITEM_ID, id.namespace, nodeConfiguration);
    }

    public ListenableFuture<Void> publishSingleton(
            Jid address,
            Extension item,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        return publish(address, item, SINGLETON_ITEM_ID, node, nodeConfiguration);
    }

    public ListenableFuture<Void> publish(
            Jid address,
            Extension item,
            final String itemId,
            final NodeConfiguration nodeConfiguration) {
        final var id = ExtensionFactory.id(item.getClass());
        return publish(address, item, itemId, id.namespace, nodeConfiguration);
    }

    public ListenableFuture<Void> publish(
            final Jid address,
            final Extension itemPayload,
            final String itemId,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        final var future = publishNoRetry(address, itemPayload, itemId, node, nodeConfiguration);
        return Futures.catchingAsync(
                future,
                PreconditionNotMetException.class,
                ex -> {
                    Log.d(
                            Config.LOGTAG,
                            "Node " + node + " on " + address + " requires reconfiguration");
                    final var reconfigurationFuture =
                            reconfigureNode(address, node, nodeConfiguration);
                    return Futures.transformAsync(
                            reconfigurationFuture,
                            ignored ->
                                    publishNoRetry(
                                            address, itemPayload, itemId, node, nodeConfiguration),
                            MoreExecutors.directExecutor());
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> publishNoRetry(
            final Jid address,
            final Extension itemPayload,
            final String itemId,
            final String node,
            final NodeConfiguration nodeConfiguration) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSub());
        final var publish = pubSub.addExtension(new Publish());
        publish.setNode(node);
        final var item = publish.addExtension(new PubSub.Item());
        item.setId(itemId);
        item.addExtension(itemPayload);
        pubSub.addExtension(PublishOptions.of(nodeConfiguration));
        final ListenableFuture<Void> iqFuture =
                Futures.transform(
                        connection.sendIqPacket(iq),
                        result -> null,
                        MoreExecutors.directExecutor());
        return Futures.catchingAsync(
                iqFuture,
                IqErrorException.class,
                new PubSubExceptionTransformer<>(),
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> reconfigureNode(
            final Jid address, final String node, final NodeConfiguration nodeConfiguration) {
        return Futures.transformAsync(
                getNodeConfiguration(address, node),
                data -> setNodeConfiguration(address, node, data.submit(nodeConfiguration)),
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Data> getNodeConfiguration(final Jid address, final String node) {
        final Iq iq = new Iq(Iq.Type.GET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSubOwner());
        final var configure = pubSub.addExtension(new Configure());
        configure.setNode(node);
        return Futures.transform(
                connection.sendIqPacket(iq),
                result -> {
                    final var pubSubOwnerResult = result.getExtension(PubSubOwner.class);
                    final Configure configureResult =
                            pubSubOwnerResult == null
                                    ? null
                                    : pubSubOwnerResult.getExtension(Configure.class);
                    if (configureResult == null) {
                        throw new IllegalStateException(
                                "No configuration found in configuration request result");
                    }
                    return configureResult.getData();
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> setNodeConfiguration(
            final Jid address, final String node, final Data data) {
        final Iq iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSubOwner());
        final var configure = pubSub.addExtension(new Configure());
        configure.setNode(node);
        configure.addExtension(data);
        return Futures.transform(
                connection.sendIqPacket(iq),
                result -> {
                    Log.d(Config.LOGTAG, "Modified node configuration " + node + " on " + address);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Iq> retract(final Jid address, final String itemId, final String node) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSub());
        final var retract = pubSub.addExtension(new Retract());
        retract.setNode(node);
        retract.setNotify(true);
        final var item = retract.addExtension(new PubSub.Item());
        item.setId(itemId);
        return connection.sendIqPacket(iq);
    }

    public ListenableFuture<Iq> delete(final Jid address, final String node) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(address);
        final var pubSub = iq.addExtension(new PubSubOwner());
        final var delete =
                pubSub.addExtension(new im.conversations.android.xmpp.model.pubsub.owner.Delete());
        delete.setNode(node);
        return connection.sendIqPacket(iq);
    }

    private static class PubSubExceptionTransformer<V>
            implements AsyncFunction<IqErrorException, V> {

        @Override
        @NonNull
        public ListenableFuture<V> apply(@NonNull IqErrorException ex) {
            final var error = ex.getError();
            if (error == null) {
                return Futures.immediateFailedFuture(ex);
            }
            final PubSubError pubSubError = error.getExtension(PubSubError.class);
            if (pubSubError instanceof PubSubError.PreconditionNotMet) {
                return Futures.immediateFailedFuture(
                        new PreconditionNotMetException(ex.getResponse()));
            } else if (pubSubError != null) {
                return Futures.immediateFailedFuture(new PubSubErrorException(ex.getResponse()));
            } else {
                return Futures.immediateFailedFuture(ex);
            }
        }
    }
}
