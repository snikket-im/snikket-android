package im.conversations.android.xmpp.model.tls;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Proceed extends StreamElement {

    public Proceed() {
        super(Proceed.class);
    }
}
