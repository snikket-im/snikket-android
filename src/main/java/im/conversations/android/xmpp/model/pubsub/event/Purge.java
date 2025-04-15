package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Purge extends Action {

    public Purge() {
        super(Purge.class);
    }

    public String getNode() {
        return this.getAttribute("node");
    }
}
