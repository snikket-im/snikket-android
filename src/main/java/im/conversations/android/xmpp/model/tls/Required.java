package im.conversations.android.xmpp.model.tls;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Required extends Extension {
    public Required() {
        super(Required.class);
    }
}
