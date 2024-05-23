package im.conversations.android.xmpp.model.pubsub.owner;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "pubsub")
public class PubSubOwner extends Extension {

    public PubSubOwner() {
        super(PubSubOwner.class);
    }
}
