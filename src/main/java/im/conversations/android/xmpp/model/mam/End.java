package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class End extends Extension {
    public End() {
        super(End.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
