package im.conversations.android.xmpp;

import im.conversations.android.xmpp.model.pubsub.error.PubSubError;
import im.conversations.android.xmpp.model.stanza.Iq;

public class PreconditionNotMetException extends PubSubErrorException {

    public PreconditionNotMetException(final Iq response) {
        super(response);
        if (this.pubSubError instanceof PubSubError.PreconditionNotMet) {
            return;
        }
        throw new AssertionError(
                "This exception should only be constructed for PreconditionNotMet errors");
    }
}
