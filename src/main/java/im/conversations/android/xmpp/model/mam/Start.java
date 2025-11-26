package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Start extends Extension {

    public Start() {
        super(Start.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
