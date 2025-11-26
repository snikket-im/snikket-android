package im.conversations.android.xmpp.model.disco.external;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Service extends Extension {

    public Service() {
        super(Service.class);
    }
}
