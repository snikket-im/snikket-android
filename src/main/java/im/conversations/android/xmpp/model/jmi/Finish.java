package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Finish extends JingleMessage {

    public Finish() {
        super(Finish.class);
    }
}
