package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.util.Patterns;
import androidx.annotation.NonNull;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.pars.PreAuth;
import im.conversations.android.xmpp.model.register.Instructions;
import im.conversations.android.xmpp.model.register.Password;
import im.conversations.android.xmpp.model.register.Register;
import im.conversations.android.xmpp.model.register.Remove;
import im.conversations.android.xmpp.model.register.Username;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Arrays;
import java.util.regex.Matcher;
import okhttp3.HttpUrl;

public class RegistrationManager extends AbstractManager {

    public RegistrationManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Void> setPassword(final String password) {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(account.getJid().getDomain());
        final var register = iq.addExtension(new Register());
        register.addUsername(account.getJid().getLocal());
        register.addPassword(password);
        return Futures.transform(
                connection.sendIqPacket(iq), r -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> unregister() {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(account.getJid().getDomain());
        final var register = iq.addExtension(new Register());
        register.addExtension(new Remove());
        return Futures.transform(
                connection.sendIqPacket(iq), r -> null, MoreExecutors.directExecutor());
    }

    public ListenableFuture<Registration> getRegistration() {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(account.getDomain());
        iq.addExtension(new Register());
        final var future = connection.sendIqPacket(iq, true);
        return Futures.transform(
                future,
                result -> {
                    final var register = result.getExtension(Register.class);
                    if (register == null) {
                        throw new IllegalStateException(
                                "Server did not include register in response");
                    }
                    if (register.hasExtension(Username.class)
                            && register.hasExtension(Password.class)) {
                        return new SimpleRegistration();
                    }
                    final var data = register.getExtension(Data.class);
                    // note that the captcha namespace is incorrect here. That namespace is only
                    // used in message challenges. ejabberd uses the incorrect namespace though
                    if (data != null
                            && Arrays.asList(Namespace.REGISTER, Namespace.CAPTCHA)
                                    .contains(data.getFormType())) {
                        return new ExtendedRegistration(data);
                    }
                    final var oob = register.getExtension(OutOfBandData.class);
                    final var instructions = register.getExtension(Instructions.class);
                    final String instructionsText =
                            instructions == null ? null : instructions.getContent();
                    final String redirectUrl = oob == null ? null : oob.getURL();
                    if (redirectUrl != null) {
                        return RedirectRegistration.ifValid(redirectUrl);
                    }
                    if (instructionsText != null) {
                        final Matcher matcher = Patterns.WEB_URL.matcher(instructionsText);
                        if (matcher.find()) {
                            final String instructionsUrl =
                                    instructionsText.substring(matcher.start(), matcher.end());
                            return RedirectRegistration.ifValid(instructionsUrl);
                        }
                    }
                    throw new IllegalStateException("No supported registration method found");
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> sendPreAuthentication(final String token) {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.GET);
        iq.setTo(account.getJid().getDomain());
        final var preAuthentication = iq.addExtension(new PreAuth());
        preAuthentication.setToken(token);
        final var future = connection.sendIqPacket(iq, true);
        return Futures.transform(future, result -> null, MoreExecutors.directExecutor());
    }

    public abstract static class Registration {}

    // only requires Username + Password
    public static class SimpleRegistration extends Registration {}

    // Captcha as shown here: https://xmpp.org/extensions/xep-0158.html#register
    public static class ExtendedRegistration extends Registration {
        private final Data data;

        public ExtendedRegistration(Data data) {
            this.data = data;
        }

        public Data getData() {
            return this.data;
        }
    }

    // Redirection as show here: https://xmpp.org/extensions/xep-0077.html#redirect
    public static class RedirectRegistration extends Registration {
        private final HttpUrl url;

        private RedirectRegistration(@NonNull HttpUrl url) {
            this.url = url;
        }

        public @NonNull HttpUrl getURL() {
            return this.url;
        }

        public static RedirectRegistration ifValid(final String url) {
            final HttpUrl httpUrl = HttpUrl.parse(url);
            if (httpUrl != null && httpUrl.isHttps()) {
                return new RedirectRegistration(httpUrl);
            }
            throw new IllegalStateException(
                    "A URL found the registration instructions is not valid");
        }
    }
}
