package im.conversations.android.xmpp.model.pubsub.owner;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;

@XmlElement
public class Configure extends Extension {

    public Configure() {
        super(Configure.class);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public Data getData() {
        return this.getExtension(Data.class);
    }
}
