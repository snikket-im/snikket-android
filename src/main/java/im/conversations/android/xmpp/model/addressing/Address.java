package im.conversations.android.xmpp.model.addressing;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Address extends Extension {
    public Address() {
        super(Address.class);
    }
}
