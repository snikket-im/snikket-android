package im.conversations.android.xmpp.model.bind;


import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Jid extends Extension {

    public Jid() {
        super(Jid.class);
    }
}
