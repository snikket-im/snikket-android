package im.conversations.android.xmpp.model.bind2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Inline extends Extension {

    public Inline() {
        super(Inline.class);
    }
}
