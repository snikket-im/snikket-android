package im.conversations.android.xmpp.model.up;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Push extends Extension implements ByteContent {

    public Push() {
        super(Push.class);
    }
}
