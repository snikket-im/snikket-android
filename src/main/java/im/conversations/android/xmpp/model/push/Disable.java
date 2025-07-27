package im.conversations.android.xmpp.model.push;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Disable extends Extension {

    public Disable() {
        super(Disable.class);
    }

    public void setJid(final Jid address) {
        this.setAttribute("jid", address);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }
}
