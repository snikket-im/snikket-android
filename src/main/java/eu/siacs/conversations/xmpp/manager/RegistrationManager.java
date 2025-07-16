package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.Patterns;
import androidx.annotation.NonNull;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.IqErrorException;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.oob.OutOfBandData;
import im.conversations.android.xmpp.model.pars.PreAuth;
import im.conversations.android.xmpp.model.register.Instructions;
import im.conversations.android.xmpp.model.register.Password;
import im.conversations.android.xmpp.model.register.Register;
import im.conversations.android.xmpp.model.register.Remove;
import im.conversations.android.xmpp.model.register.Username;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Arrays;
import java.util.List;
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
                connection.sendIqPacket(iq),
                r -> {
                    account.setPassword(password);
                    account.setOption(Account.OPTION_MAGIC_CREATE, false);
                    getDatabase().updateAccount(account);
                    return null;
                },
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> register() {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(account.getJid().getDomain());
        final var register = iq.addExtension(new Register());
        register.addUsername(account.getJid().getLocal());
        register.addPassword(account.getPassword());
        final ListenableFuture<Void> future =
                Futures.transform(
                        connection.sendIqPacket(iq, true),
                        result -> null,
                        MoreExecutors.directExecutor());
        return Futures.catchingAsync(
                future,
                IqErrorException.class,
                ex ->
                        Futures.immediateFailedFuture(
                                new RegistrationFailedException(ex.getResponse())),
                MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> register(final Data data, final String ocr) {
        final var account = getAccount();
        final var submission =
                data.submit(
                        ImmutableMap.of(
                                "username",
                                account.getJid().getLocal(),
                                "password",
                                account.getPassword(),
                                "ocr",
                                ocr));
        final var iq = new Iq(Iq.Type.SET);
        final var register = iq.addExtension(new Register());
        register.addExtension(submission);
        final ListenableFuture<Void> future =
                Futures.transform(
                        connection.sendIqPacket(iq, true),
                        result -> null,
                        MoreExecutors.directExecutor());
        return Futures.catchingAsync(
                future,
                IqErrorException.class,
                ex ->
                        Futures.immediateFailedFuture(
                                new RegistrationFailedException(ex.getResponse())),
                MoreExecutors.directExecutor());
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
        return Futures.transformAsync(
                future,
                result -> {
                    final var register = result.getExtension(Register.class);
                    if (register == null) {
                        throw new IllegalStateException(
                                "Server did not include register in response");
                    }
                    if (register.hasExtension(Username.class)
                            && register.hasExtension(Password.class)) {
                        return Futures.immediateFuture(new SimpleRegistration());
                    }

                    // find bits of binary and get captcha from there

                    final var data = register.getExtension(Data.class);
                    // note that the captcha namespace is incorrect here. That namespace is only
                    // used in message challenges. ejabberd uses the incorrect namespace though
                    if (data != null
                            && Arrays.asList(Namespace.REGISTER, Namespace.CAPTCHA)
                                    .contains(data.getFormType())) {
                        return getExtendedRegistration(register, data);
                    }
                    final var oob = register.getExtension(OutOfBandData.class);
                    final var instructions = register.getExtension(Instructions.class);
                    final String instructionsText =
                            instructions == null ? null : instructions.getContent();
                    final String redirectUrl = oob == null ? null : oob.getURL();
                    if (redirectUrl != null) {
                        return Futures.immediateFuture(RedirectRegistration.ifValid(redirectUrl));
                    }
                    if (instructionsText != null) {
                        final Matcher matcher = Patterns.WEB_URL.matcher(instructionsText);
                        if (matcher.find()) {
                            final String instructionsUrl =
                                    instructionsText.substring(matcher.start(), matcher.end());
                            return Futures.immediateFuture(
                                    RedirectRegistration.ifValid(instructionsUrl));
                        }
                    }
                    throw new IllegalStateException("No supported registration method found");
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<Registration> getExtendedRegistration(
            final Register register, final Data data) {
        final var ocr = data.getFieldByName("ocr");
        if (ocr == null) {
            throw new IllegalArgumentException("Missing OCR form field");
        }
        final var ocrMedia = ocr.getMedia();
        if (ocrMedia == null) {
            throw new IllegalArgumentException("OCR form field missing media");
        }
        final var uris = ocrMedia.getUris();
        final var bobUri = Iterables.find(uris, u -> "cid".equals(u.getScheme()), null);
        final Optional<im.conversations.android.xmpp.model.bob.Data> bob;
        if (bobUri != null) {
            bob = im.conversations.android.xmpp.model.bob.Data.get(register, bobUri.getPath());
        } else {
            bob = Optional.absent();
        }
        if (bob.isPresent()) {
            final var bytes = bob.get().asBytes();
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            return Futures.immediateFuture(new ExtendedRegistration(bitmap, data));
        }
        final var captchaFallbackUrl = data.getValue("captcha-fallback-url");
        if (captchaFallbackUrl == null) {
            throw new IllegalStateException("No captcha fallback URL provided");
        }
        final var captchFallbackHttpUrl = HttpUrl.parse(captchaFallbackUrl);
        Log.d(Config.LOGTAG, "fallback url: " + captchFallbackHttpUrl);
        throw new IllegalStateException("Not implemented");
    }

    public ListenableFuture<Registration> getRegistration(final String token) {
        final var preAuthentication = sendPreAuthentication(token);
        final var caught =
                Futures.catchingAsync(
                        preAuthentication,
                        IqErrorException.class,
                        ex -> {
                            Log.d(Config.LOGTAG, "could not pre authenticate registration", ex);
                            final var error = ex.getError();
                            final var condition = error == null ? null : error.getCondition();
                            if (condition instanceof Condition.ItemNotFound) {
                                return Futures.immediateFailedFuture(
                                        new InvalidTokenException(ex.getResponse()));
                            } else {
                                return Futures.immediateFuture(ex);
                            }
                        },
                        MoreExecutors.directExecutor());
        return Futures.transformAsync(
                caught, v -> getRegistration(), MoreExecutors.directExecutor());
    }

    public ListenableFuture<Void> sendPreAuthentication(final String token) {
        final var account = getAccount();
        final var iq = new Iq(Iq.Type.SET);
        iq.setTo(account.getJid().getDomain());
        final var preAuthentication = iq.addExtension(new PreAuth());
        preAuthentication.setToken(token);
        final var future = connection.sendIqPacket(iq, true);
        return Futures.transform(future, result -> null, MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class).hasServerFeature(Namespace.REGISTER);
    }

    public abstract static class Registration {}

    // only requires Username + Password
    public static class SimpleRegistration extends Registration {}

    // Captcha as shown here: https://xmpp.org/extensions/xep-0158.html#register
    public static class ExtendedRegistration extends Registration {
        private final Bitmap captcha;
        private final Data data;

        public ExtendedRegistration(final Bitmap captcha, final Data data) {
            this.captcha = captcha;
            this.data = data;
        }

        public Bitmap getCaptcha() {
            return this.captcha;
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

    public static class InvalidTokenException extends IqErrorException {

        public InvalidTokenException(final Iq response) {
            super(response);
        }
    }

    public static class RegistrationFailedException extends IqErrorException {

        private final List<String> PASSWORD_TOO_WEAK_MESSAGES =
                Arrays.asList("The password is too weak", "Please use a longer password.");

        public RegistrationFailedException(final Iq response) {
            super(response);
        }

        public Account.State asAccountState() {
            final var error = getError();
            final var condition = error == null ? null : error.getCondition();
            if (condition instanceof Condition.Conflict) {
                return Account.State.REGISTRATION_CONFLICT;
            } else if (condition instanceof Condition.ResourceConstraint) {
                return Account.State.REGISTRATION_PLEASE_WAIT;
            } else if (condition instanceof Condition.NotAcceptable
                    && PASSWORD_TOO_WEAK_MESSAGES.contains(error.getTextAsString())) {
                return Account.State.REGISTRATION_PASSWORD_TOO_WEAK;
            } else {
                return Account.State.REGISTRATION_FAILED;
            }
        }
    }
}
