package im.conversations.android.xmpp.model.carbons;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Enable extends Extension {

    public Enable() {
        super(Enable.class);
    }
}
