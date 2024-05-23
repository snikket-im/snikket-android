package im.conversations.android.xmpp.model.unique;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class OriginId extends Extension {

    public OriginId() {
        super(OriginId.class);
    }
}
