package im.conversations.android.xmpp.model.rsm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Max extends Extension {

    public Max() {
        super(Max.class);
    }

    public void setMax(final int max) {
        this.setContent(String.valueOf(max));
    }
}
