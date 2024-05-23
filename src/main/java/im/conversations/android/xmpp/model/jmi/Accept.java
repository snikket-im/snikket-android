package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Accept extends JingleMessage {

    public Accept() {
        super(Accept.class);
    }
}
