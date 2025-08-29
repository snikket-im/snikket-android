package im.conversations.android.xmpp.model.state;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public final class Composing extends ChatStateNotification {

    public Composing() {
        super(Composing.class);
    }
}
