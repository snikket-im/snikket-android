package im.conversations.android.xmpp.model.muc.user;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Destroy extends Extension {

    public Destroy() {
        super(Destroy.class);
    }

    public Jid getJid() {
        return this.getAttributeAsJid("jid");
    }
}
