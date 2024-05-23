package im.conversations.android.xmpp.model.sasl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Mechanism extends Extension {

    public Mechanism() {
        super(Mechanism.class);
    }
}
