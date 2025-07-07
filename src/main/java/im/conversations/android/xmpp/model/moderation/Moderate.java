package im.conversations.android.xmpp.model.moderation;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Moderate extends Extension {

    public Moderate() {
        super(Moderate.class);
    }

    public Moderate(final String id) {
        this();
        this.setId(id);
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }
}
