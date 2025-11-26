package im.conversations.android.xmpp.model.rsm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class After extends Extension {

    public After() {
        super(After.class);
    }
}
