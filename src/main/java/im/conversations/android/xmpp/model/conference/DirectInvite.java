package im.conversations.android.xmpp.model.conference;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x")
public class DirectInvite extends Extension {

    public DirectInvite() {
        super(DirectInvite.class);
    }

    public void setJid(final Jid jid) {
        this.setAttribute("jid", jid);
    }

    public void setPassword(final String password) {
        this.setAttribute("password", password);
    }
}
