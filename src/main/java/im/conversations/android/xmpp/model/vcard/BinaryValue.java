package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "BINVAL")
public class BinaryValue extends Extension implements ByteContent {

    public BinaryValue() {
        super(BinaryValue.class);
    }
}
