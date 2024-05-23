package im.conversations.android.xmpp.model.jabber;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Status extends Extension {


    public Status() {
        super(Status.class);
    }
}
