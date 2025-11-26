package im.conversations.android.xmpp.model.data;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Value extends Extension {

    public Value() {
        super(Value.class);
    }
}
