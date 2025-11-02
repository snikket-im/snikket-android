package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Reject extends JingleMessage {

    public Reject() {
        super(Reject.class);
    }
}
