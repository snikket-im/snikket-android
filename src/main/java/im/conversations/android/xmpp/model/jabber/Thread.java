package im.conversations.android.xmpp.model.jabber;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Thread extends Extension {

    public Thread() {
        super(Thread.class);
    }
}
