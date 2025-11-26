package im.conversations.android.xmpp.model.disco.external;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Services extends Extension {

    public Services() {
        super(Services.class);
    }
}
