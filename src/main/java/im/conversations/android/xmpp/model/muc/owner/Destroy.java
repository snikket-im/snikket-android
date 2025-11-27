package im.conversations.android.xmpp.model.muc.owner;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Destroy extends Extension {

    public Destroy() {
        super(Destroy.class);
    }
}
