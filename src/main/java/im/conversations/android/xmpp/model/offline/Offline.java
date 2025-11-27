package im.conversations.android.xmpp.model.offline;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Offline extends Extension {

    public Offline() {
        super(Offline.class);
    }
}
