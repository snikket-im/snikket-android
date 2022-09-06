package eu.siacs.conversations.crypto.sasl;

import com.google.common.base.Strings;

import java.util.Collection;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public abstract class SaslMechanism {

    protected final Account account;

    protected SaslMechanism(final Account account) {
        this.account = account;
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

    public String getClientFirstMessage() {
        return "";
    }

    public String getResponse(final String challenge) throws AuthenticationException {
        return "";
    }

    protected enum State {
        INITIAL,
        AUTH_TEXT_SENT,
        RESPONSE_SENT,
        VALID_SERVER_RESPONSE,
    }

    public enum Version {
        SASL,
        SASL_2;

        public static Version of(final Element element) {
            switch (Strings.nullToEmpty(element.getNamespace())) {
                case Namespace.SASL:
                    return SASL;
                case Namespace.SASL_2:
                    return SASL_2;
                default:
                    throw new IllegalArgumentException("Unrecognized SASL namespace");
            }
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

        public SaslMechanism of(
                final Collection<String> mechanisms, final Collection<ChannelBinding> bindings) {
            if (mechanisms.contains(External.MECHANISM) && account.getPrivateKeyAlias() != null) {
                return new External(account);
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
    }
}
