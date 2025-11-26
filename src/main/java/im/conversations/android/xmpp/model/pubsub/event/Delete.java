package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Delete extends Action {

    public Delete() {
        super(Delete.class);
    }
}
