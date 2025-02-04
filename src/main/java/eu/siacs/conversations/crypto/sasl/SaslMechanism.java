package eu.siacs.conversations.crypto.sasl;

import android.util.Log;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.SSLSockets;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import java.util.Collection;
import java.util.Collections;
import javax.net.ssl.SSLSocket;

public abstract class SaslMechanism {

    protected final Account account;

    protected State state = State.INITIAL;

    protected SaslMechanism(final Account account) {
        this.account = account;
    }

    public static String namespace(final Version version) {
        if (version == Version.SASL) {
            return Namespace.SASL;
        } else {
            return Namespace.SASL_2;
        }
    }

    /**
     * The priority is used to pin the authentication mechanism. If authentication fails, it MAY be
     * retried with another mechanism of the same priority, but MUST NOT be tried with a mechanism
     * of lower priority (to prevent downgrade attacks).
     *
     * @return An arbitrary int representing the priority
     */
    public abstract int getPriority();

    public abstract String getMechanism();

    public abstract String getClientFirstMessage(final SSLSocket sslSocket);

    public abstract String getResponse(final String challenge, final SSLSocket sslSocket)
            throws AuthenticationException;

    public enum State {
        INITIAL,
        AUTH_TEXT_SENT,
        RESPONSE_SENT,
        VALID_SERVER_RESPONSE,
    }

    protected void checkState(final State expected) throws InvalidStateException {
        final var current = this.state;
        if (current == null) {
            throw new InvalidStateException("Current state is null. Implementation problem");
        }
        if (current != expected) {
            throw new InvalidStateException(
                    String.format("State was %s. Expected %s", current, expected));
        }
    }

    public enum Version {
        SASL,
        SASL_2;

        public static Version of(final Element element) {
            return switch (Strings.nullToEmpty(element.getNamespace())) {
                case Namespace.SASL -> SASL;
                case Namespace.SASL_2 -> SASL_2;
                default -> throw new IllegalArgumentException("Unrecognized SASL namespace");
            };
        }
    }

    public static class AuthenticationException extends Exception {
        public AuthenticationException(final String message) {
            super(message);
        }

        public AuthenticationException(final Exception inner) {
            super(inner);
        }

        public AuthenticationException(final String message, final Exception exception) {
            super(message, exception);
        }
    }

    public static class InvalidStateException extends AuthenticationException {
        public InvalidStateException(final String message) {
            super(message);
        }

        public InvalidStateException(final State state) {
            this("Invalid state: " + state.toString());
        }
    }

    public static final class Factory {

        private final Account account;

        public Factory(final Account account) {
            this.account = account;
        }

        private SaslMechanism of(
                final Collection<String> mechanisms, final ChannelBinding channelBinding) {
            Preconditions.checkNotNull(channelBinding, "Use ChannelBinding.NONE instead of null");
            if (mechanisms.contains(External.MECHANISM) && account.getPrivateKeyAlias() != null) {
                return new External(account);
            } else if (mechanisms.contains(ScramSha512Plus.MECHANISM)
                    && channelBinding != ChannelBinding.NONE) {
                return new ScramSha512Plus(account, channelBinding);
            } else if (mechanisms.contains(ScramSha256Plus.MECHANISM)
                    && channelBinding != ChannelBinding.NONE) {
                return new ScramSha256Plus(account, channelBinding);
            } else if (mechanisms.contains(ScramSha1Plus.MECHANISM)
                    && channelBinding != ChannelBinding.NONE) {
                return new ScramSha1Plus(account, channelBinding);
            } else if (mechanisms.contains(ScramSha512.MECHANISM)) {
                return new ScramSha512(account);
            } else if (mechanisms.contains(ScramSha256.MECHANISM)) {
                return new ScramSha256(account);
            } else if (mechanisms.contains(ScramSha1.MECHANISM)) {
                return new ScramSha1(account);
            } else if (mechanisms.contains(Plain.MECHANISM)
                    && !account.getServer().equals("nimbuzz.com")) {
                return new Plain(account);
            } else if (mechanisms.contains(DigestMd5.MECHANISM)) {
                return new DigestMd5(account);
            } else if (mechanisms.contains(Anonymous.MECHANISM)) {
                return new Anonymous(account);
            } else {
                return null;
            }
        }

        public SaslMechanism of(
                final Collection<String> mechanisms,
                final Collection<ChannelBinding> bindings,
                final Version version,
                final SSLSockets.Version sslVersion) {
            final HashedToken fastMechanism = account.getFastMechanism();
            if (version == Version.SASL_2 && fastMechanism != null) {
                return fastMechanism;
            }
            final ChannelBinding channelBinding = ChannelBinding.best(bindings, sslVersion);
            return of(mechanisms, channelBinding);
        }

        public SaslMechanism of(final String mechanism, final ChannelBinding channelBinding) {
            return of(Collections.singleton(mechanism), channelBinding);
        }
    }

    public static SaslMechanism ensureAvailable(
            final SaslMechanism mechanism,
            final SSLSockets.Version sslVersion,
            final boolean requireChannelBinding) {
        if (mechanism instanceof ChannelBindingMechanism) {
            final ChannelBinding cb = ((ChannelBindingMechanism) mechanism).getChannelBinding();
            if (ChannelBinding.isAvailable(cb, sslVersion)) {
                return mechanism;
            } else {
                Log.d(
                        Config.LOGTAG,
                        "pinned channel binding method " + cb + " no longer available");
                return null;
            }
        } else if (requireChannelBinding) {
            Log.d(Config.LOGTAG, "pinned mechanism did not provide channel binding");
            return null;
        } else {
            return mechanism;
        }
    }

    public static boolean hashedToken(final SaslMechanism saslMechanism) {
        return saslMechanism instanceof HashedToken;
    }

    public static boolean pin(final SaslMechanism saslMechanism) {
        return !hashedToken(saslMechanism);
    }
}
