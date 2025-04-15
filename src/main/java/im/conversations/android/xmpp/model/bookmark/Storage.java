package im.conversations.android.xmpp.model.bookmark;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Storage extends Extension {

    public Storage() {
        super(Storage.class);
    }
}
