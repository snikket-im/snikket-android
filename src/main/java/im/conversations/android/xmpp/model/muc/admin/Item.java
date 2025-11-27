package im.conversations.android.xmpp.model.muc.admin;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.muc.Affiliation;
import im.conversations.android.xmpp.model.muc.Role;

@XmlElement
public class Item extends im.conversations.android.xmpp.model.muc.Item {

    public Item() {
        super(Item.class);
    }

    public void setAffiliation(final Affiliation affiliation) {
        this.setAttribute("affiliation", affiliation);
    }

    public void setRole(final Role role) {
        this.setAttribute("role", role);
    }

    public void setJid(final Jid jid) {
        this.setAttribute("jid", jid);
    }

    public void setNick(String user) {
        this.setAttribute("nick", user);
    }
}
