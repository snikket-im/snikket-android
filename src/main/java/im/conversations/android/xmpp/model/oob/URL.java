package im.conversations.android.xmpp.model.oob;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "url")
public class URL extends Extension {

    public URL() {
        super(URL.class);
    }
}
