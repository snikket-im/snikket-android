package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Purge extends Extension {

    public Purge() {
        super(Purge.class);
    }

    public String getNode() {
        return this.getAttribute("node");
    }
}
