package im.conversations.android.xmpp.model.pubsub;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Publish extends Extension {

    public Publish() {
        super(Publish.class);
    }

    public void setNode(String node) {
        this.setAttribute("node", node);
    }
}
