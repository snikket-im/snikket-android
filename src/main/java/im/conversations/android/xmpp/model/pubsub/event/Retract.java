package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Retract extends Extension {

    public Retract() {
        super(Retract.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
