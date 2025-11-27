package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.commands.Command;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.push.Disable;
import im.conversations.android.xmpp.model.push.Enable;
import im.conversations.android.xmpp.model.stanza.Iq;

public class PushNotificationManager extends AbstractManager {

    public PushNotificationManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Registration> register(
            final Jid appServer, final String fcmToken, final String androidId) {
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(appServer);
        final var command =
                iq.addExtension(new Command("register-push-fcm", Command.Action.EXECUTE));
        command.addExtension(
                Data.of(ImmutableMap.of("token", fcmToken, "android-id", androidId), null));
        return Futures.transform(
                connection.sendIqPacket(iq),
                response -> {
                    final var result = response.getExtension(Command.class);
                    if (result == null) {
                        throw new IllegalStateException("No command in response");
                    }
                    final var data = result.getData();
                    if (data == null) {
                        throw new IllegalStateException("No data in command");
                    }
                    final var node = data.getValue("node");
                    final var secret = data.getValue("secret");
                    if (Strings.isNullOrEmpty(node) || Strings.isNullOrEmpty(secret)) {
                        throw new IllegalStateException("Missing node or secret in response");
                    }
                    return new Registration(appServer, node, secret);
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> enable(final Registration registration) {
        final var iq = new Iq(Iq.Type.SET);
        final var enable = iq.addExtension(new Enable());
        enable.setJid(registration.address);
        enable.setNode(registration.node);
        enable.addExtension(
                Data.of(
                        ImmutableMap.of("secret", registration.secret),
                        Namespace.PUB_SUB_PUBLISH_OPTIONS));
        return Futures.transform(
                connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> disable(final Jid appServer, final String node) {
        final var iq = new Iq(Iq.Type.SET);
        final var disable = iq.addExtension(new Disable());
        disable.setJid(appServer);
        disable.setNode(node);
        return Futures.transform(
                connection.sendIqPacket(iq), response -> null, MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasAccountFeature(Namespace.PUSH);
    }

    public static class Registration {
        public final Jid address;
        public final String node;
        public final String secret;

        public Registration(Jid address, String node, String secret) {
            this.address = address;
            this.node = node;
            this.secret = secret;
        }
    }
}
