package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Active extends ChatStateNotification {

    public Active() {
        super(Active.class);
    }
}
