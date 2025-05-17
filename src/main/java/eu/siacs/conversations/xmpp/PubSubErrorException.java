package im.conversations.android.xmpp;

import im.conversations.android.xmpp.model.pubsub.error.PubSubError;
import im.conversations.android.xmpp.model.stanza.Iq;

public class PubSubErrorException extends IqErrorException {

    protected final PubSubError pubSubError;

    public PubSubErrorException(Iq response) {
        super(response);
        final var error = response.getError();
        final var pubSubError = error == null ? null : error.getExtension(PubSubError.class);
        if (pubSubError == null) {
            throw new AssertionError("This exception should only be constructed for PubSubErrors");
        }
        this.pubSubError = pubSubError;
    }
}
