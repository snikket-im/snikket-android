package im.conversations.android.xmpp.model.session;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Optional extends Extension {

    public Optional() {
        super(Optional.class);
    }
}
