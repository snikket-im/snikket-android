package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Map;

public class PepManager extends AbstractManager {

    public PepManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public <T extends Extension> ListenableFuture<Map<String, T>> fetchItems(final Class<T> clazz) {
        return pubSubManager().fetchItems(pepService(), clazz);
    }

    public <T extends Extension> ListenableFuture<T> fetchMostRecentItem(
            final String node, final Class<T> clazz) {
        return pubSubManager().fetchMostRecentItem(pepService(), node, clazz);
    }

    public ListenableFuture<Void> publish(
            Extension item, final String itemId, final NodeConfiguration nodeConfiguration) {
        return pubSubManager().publish(pepService(), item, itemId, nodeConfiguration);
    }

    public ListenableFuture<Void> publishSingleton(
            Extension item, final String node, final NodeConfiguration nodeConfiguration) {
        return pubSubManager().publishSingleton(pepService(), item, node, nodeConfiguration);
    }

    public ListenableFuture<Void> publishSingleton(
            final Extension item, final NodeConfiguration nodeConfiguration) {
        return pubSubManager().publishSingleton(pepService(), item, nodeConfiguration);
    }

    public ListenableFuture<Iq> retract(final String itemId, final String node) {
        return pubSubManager().retract(pepService(), itemId, node);
    }

    public ListenableFuture<Void> delete(final String node) {
        final var future = pubSubManager().delete(pepService(), node);
        return Futures.transform(future, iq -> null, MoreExecutors.directExecutor());
    }

    public boolean hasPublishOptions() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.PUBSUB_PUBLISH_OPTIONS);
    }

    public boolean hasConfigNodeMax() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.PUBSUB_CONFIG_NODE_MAX);
    }

    private PubSubManager pubSubManager() {
        return getManager(PubSubManager.class);
    }

    private Jid pepService() {
        return connection.getAccount().getJid().asBareJid();
    }
}
