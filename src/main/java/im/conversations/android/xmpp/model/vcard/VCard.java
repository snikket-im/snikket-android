package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "vCard")
public class VCard extends Extension {

    public VCard() {
        super(VCard.class);
    }
}
