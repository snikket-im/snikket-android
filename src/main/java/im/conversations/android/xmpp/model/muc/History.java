package im.conversations.android.xmpp.model.muc;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class History extends Extension {

    public History() {
        super(History.class);
    }

    public void setMaxChars(final int maxChars) {
        this.setAttribute("maxchars", maxChars);
    }

    public void setMaxStanzas(final int maxStanzas) {
        this.setAttribute("maxstanzas", maxStanzas);
    }
}
