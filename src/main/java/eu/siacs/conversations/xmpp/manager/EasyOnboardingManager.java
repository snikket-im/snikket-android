package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.EasyOnboardingInvite;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.commands.Command;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Collections;
import java.util.List;
import okhttp3.HttpUrl;

public class EasyOnboardingManager extends AbstractManager {

    public EasyOnboardingManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<EasyOnboardingInvite> get() {
        final var optional = getAddressForCommand();
        final Jid address;
        if (optional.isPresent()) {
            address = optional.get();
        } else {
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
                    if (Strings.isNullOrEmpty(uri)) {
                        throw new IllegalStateException("missing uri");
                    }
                    if (Strings.isNullOrEmpty(landingUrl)) {
                        return new EasyOnboardingInvite(getAccount().getDomain(), uri);
                    }
                    // HttpUrl.get will throw on invalid URL
                    return new EasyOnboardingInvite(
                            getAccount().getDomain(), uri, HttpUrl.get(landingUrl));
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getAddressForCommand().isPresent();
    }

    private Optional<Jid> getAddressForCommand() {
        final var discoManager = this.getManager(DiscoManager.class);
        final var address = discoManager.getAddressForCommand(Namespace.EASY_ONBOARDING_INVITE);
        return Optional.fromNullable(address);
    }

    public static boolean anyHasSupport(final XmppConnectionService service) {
        if (QuickConversationsService.isQuicksy()) {
            return false;
        }
        return !getAccounts(service).isEmpty();
    }

    public static List<Account> getAccounts(final XmppConnectionService service) {
        if (service == null) {
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(
                Collections2.filter(
                        service.getAccounts(),
                        a ->
                                a.getXmppConnection()
                                        .getManager(EasyOnboardingManager.class)
                                        .hasFeature()));
    }
}
