package im.conversations.android.xmpp.model.markers;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Displayed extends Extension {

    public Displayed() {
        super(Displayed.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
