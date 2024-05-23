package im.conversations.android.xmpp.model.data;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Option extends Extension {

    public Option() {
        super(Option.class);
    }
}
