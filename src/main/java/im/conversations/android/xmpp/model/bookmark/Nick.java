package im.conversations.android.xmpp.model.bookmark;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Nick extends Extension {

    public Nick() {
        super(Nick.class);
    }
}
