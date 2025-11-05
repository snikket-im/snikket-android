package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Ringing extends JingleMessage {

    public Ringing() {
        super(Ringing.class);
    }
}
