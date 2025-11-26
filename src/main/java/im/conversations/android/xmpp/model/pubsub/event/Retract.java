package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Retract extends Action {

    public Retract() {
        super(Retract.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
