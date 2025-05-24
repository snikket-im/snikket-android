package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "TYPE")
public class Type extends Extension {

    public Type() {
        super(Type.class);
    }
}
