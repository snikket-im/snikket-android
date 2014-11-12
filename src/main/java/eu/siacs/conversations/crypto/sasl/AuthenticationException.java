package eu.siacs.conversations.crypto.sasl;

public class AuthenticationException extends Exception {
    public AuthenticationException(final String message) {
        super(message);
    }

    public AuthenticationException(final Exception inner) {
        super(inner);
    }
}
