package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.commands.Command;
import im.conversations.android.xmpp.model.stanza.Iq;
import okhttp3.HttpUrl;

public class EasyOnboardingManager extends AbstractManager {

    public EasyOnboardingManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<EasyOnboardingInvite> get() {
        final var discoManager = this.getManager(DiscoManager.class);
        final var address = discoManager.getAddressForCommand(Namespace.EASY_ONBOARDING_INVITE);
        if (address == null) {
            return Futures.immediateFailedFuture(
                    new UnsupportedOperationException(
                            "Server does not support generating easy onboarding invites"));
        }
        final Iq request = new Iq(Iq.Type.SET);
        request.setTo(address);
        request.addExtension(new Command(Namespace.EASY_ONBOARDING_INVITE, Command.Action.EXECUTE));
        final var future = this.connection.sendIqPacket(request);
        return Futures.transform(
                future,
                response -> {
                    final var command = response.getExtension(Command.class);
                    if (command == null) {
                        throw new IllegalStateException("No command in response");
                    }
                    final var data = command.getData();
                    if (data == null) {
                        throw new IllegalStateException("No data in command");
                    }
                    final var uri = data.getValue("uri");
                    final var landingUrl = data.getValue("landing-url");
                    if (Strings.isNullOrEmpty(uri) || Strings.isNullOrEmpty(landingUrl)) {
                        throw new IllegalStateException("missing landing url or uri");
                    }
                    // HttpUrl.get will throw on invalid URL
                    return new EasyOnboardingInvite(
                            getAccount().getDomain(), uri, HttpUrl.get(landingUrl));
                },
                MoreExecutors.directExecutor());
    }
}
