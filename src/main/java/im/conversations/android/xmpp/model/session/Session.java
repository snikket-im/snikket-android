package im.conversations.android.xmpp.model.session;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement
public class Session extends StreamFeature {

    public Session() {
        super(Session.class);
    }

    public boolean isOptional() {
        return hasExtension(Optional.class);
    }
}
