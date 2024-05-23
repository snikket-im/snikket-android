package im.conversations.android.xmpp.model.csi;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Inactive extends StreamElement {

    public Inactive() {
        super(Inactive.class);
    }
}
