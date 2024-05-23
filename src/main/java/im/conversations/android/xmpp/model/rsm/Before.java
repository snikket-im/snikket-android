package im.conversations.android.xmpp.model.rsm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Before extends Extension {

    public Before() {
        super(Before.class);
    }
}
