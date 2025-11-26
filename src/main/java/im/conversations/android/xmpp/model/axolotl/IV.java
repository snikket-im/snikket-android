package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "iv")
public class IV extends Extension implements ByteContent {

    public IV() {
        super(IV.class);
    }
}
