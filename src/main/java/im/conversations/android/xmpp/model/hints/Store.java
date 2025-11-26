package im.conversations.android.xmpp.model.hints;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Store extends Extension {

    public Store() {
        super(Store.class);
    }
}
