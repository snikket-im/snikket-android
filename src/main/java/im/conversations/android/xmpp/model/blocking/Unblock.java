package im.conversations.android.xmpp.model.blocking;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Unblock extends Extension {

    public Unblock() {
        super(Unblock.class);
    }
}
