package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Inactive extends ChatStateNotification {

    public Inactive() {
        super(Inactive.class);
    }
}
