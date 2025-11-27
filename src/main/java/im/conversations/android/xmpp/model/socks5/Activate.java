package im.conversations.android.xmpp.model.socks5;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Activate extends Extension {

    public Activate() {
        super(Activate.class);
    }

    public Activate(final Jid jid) {
        this();
        this.setContent(jid.toString());
    }
}
