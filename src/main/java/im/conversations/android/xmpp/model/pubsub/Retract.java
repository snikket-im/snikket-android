package im.conversations.android.xmpp.model.pubsub;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Retract extends Extension {

    public Retract() {
        super(Retract.class);
    }

    public void setNode(String node) {
        this.setAttribute("node", node);
    }

    public void setNotify(boolean notify) {
        this.setAttribute("notify", notify ? 1 : 0);
    }
}
