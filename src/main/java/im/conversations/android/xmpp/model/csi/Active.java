package im.conversations.android.xmpp.model.csi;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Active extends StreamElement {

    public Active() {
        super(Active.class);
    }
}
