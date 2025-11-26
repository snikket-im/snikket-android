package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Payload extends Extension implements ByteContent {

    public Payload() {
        super(Payload.class);
    }
}
