package im.conversations.android.xmpp.model.pubsub.owner;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Delete extends Extension {

    public Delete() {
        super(Delete.class);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }
}
