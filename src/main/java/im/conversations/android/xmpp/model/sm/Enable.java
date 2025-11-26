package im.conversations.android.xmpp.model.sm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Enable extends StreamElement {

    public Enable() {
        super(Enable.class);
        this.setAttribute("resume", "true");
    }
}
