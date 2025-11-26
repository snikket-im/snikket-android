package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Gone extends ChatStateNotification {

    public Gone() {
        super(Gone.class);
    }
}
