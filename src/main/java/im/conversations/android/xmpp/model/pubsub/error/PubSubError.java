package im.conversations.android.xmpp.model.pubsub.error;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

public abstract class PubSubError extends Extension {

    private PubSubError(Class<? extends PubSubError> clazz) {
        super(clazz);
    }

    @XmlElement
    public static class PreconditionNotMet extends PubSubError {

        public PreconditionNotMet() {
            super(PreconditionNotMet.class);
        }
    }
}
