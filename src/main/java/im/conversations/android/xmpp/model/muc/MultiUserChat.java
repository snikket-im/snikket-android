package im.conversations.android.xmpp.model.muc;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x")
public class MultiUserChat extends Extension {

    public MultiUserChat() {
        super(MultiUserChat.class);
    }
}
