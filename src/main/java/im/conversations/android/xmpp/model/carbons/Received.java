package im.conversations.android.xmpp.model.carbons;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.forward.Forwarded;

@XmlElement
public class Received extends Extension {

    public Received() {
        super(Received.class);
    }

    public Forwarded getForwarded() {
        return this.getExtension(Forwarded.class);
    }
}
