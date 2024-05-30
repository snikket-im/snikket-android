package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Software extends Extension {

    public Software() {
        super(Software.class);
    }

    public Software(final String software) {
        this();
        this.setContent(software);
    }
}
