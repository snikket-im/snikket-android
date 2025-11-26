package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "prekeys")
public class PreKeys extends Extension {

    public PreKeys() {
        super(PreKeys.class);
    }
}
