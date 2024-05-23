package im.conversations.android.xmpp.model.bind2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Bound extends Extension {
    public Bound() {
        super(Bound.class);
    }
}
