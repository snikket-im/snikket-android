package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Retract extends JingleMessage {

    public Retract() {
        super(Retract.class);
    }
}
