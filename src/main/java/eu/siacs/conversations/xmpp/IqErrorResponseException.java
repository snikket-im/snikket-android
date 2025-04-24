package eu.siacs.conversations.xmpp;

import im.conversations.android.xmpp.model.stanza.Iq;

public class IqErrorResponseException extends Exception {

    private final Iq response;

    public IqErrorResponseException(final Iq response) {
        super(message(response));
        this.response = response;
    }

    public Iq getResponse() {
        return this.response;
    }

    public static String message(final Iq iq) {
        final var error = iq.getError();
        if (error == null) {
            return "missing error element in response";
        }
        final var text = error.getTextAsString();
        if (text != null) {
            return text;
        }
        final var condition = error.getCondition();
        if (condition != null) {
            return condition.getName();
        }
        return "no condition attached to error";
    }
}
