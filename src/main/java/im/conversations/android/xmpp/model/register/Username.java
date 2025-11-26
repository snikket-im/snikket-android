package im.conversations.android.xmpp.model.register;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Username extends Extension {

    public Username() {
        super(Username.class);
    }
}
