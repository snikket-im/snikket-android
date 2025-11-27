package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Paused extends ChatStateNotification {

    public Paused() {
        super(Paused.class);
    }
}
