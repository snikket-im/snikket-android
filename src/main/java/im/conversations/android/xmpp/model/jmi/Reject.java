package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Reject extends JingleMessage {

    public Reject() {
        super(Reject.class);
    }
}
