package im.conversations.android.xmpp;

import com.google.common.base.Strings;
import im.conversations.android.xmpp.model.error.Condition;
import im.conversations.android.xmpp.model.error.Error;
import im.conversations.android.xmpp.model.stanza.Iq;

public class IqErrorException extends Exception {

    private final Iq response;

    public IqErrorException(Iq response) {
        super(getErrorText(response));
        this.response = response;
    }

    public Error getError() {
        return this.response.getError();
    }

    public Condition getErrorCondition() {
        final var error = getError();
        if (error == null) {
            return null;
        }
        return error.getCondition();
    }

    private static String getErrorText(final Iq response) {
        final var error = response.getError();
        final var text = error == null ? null : error.getText();
        final var textContent = text == null ? null : text.getContent();
        if (Strings.isNullOrEmpty(textContent)) {
            final var condition = error == null ? null : error.getExtension(Condition.class);
            return condition == null ? null : condition.getName();
        }
        return textContent;
    }

    public Iq getResponse() {
        return this.response;
    }
}
