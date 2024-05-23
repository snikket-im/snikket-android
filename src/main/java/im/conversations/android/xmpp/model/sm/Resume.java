package im.conversations.android.xmpp.model.sm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Resume extends StreamElement {

    public Resume() {
        super(Resume.class);
    }

    public Resume(final String id, final int sequence) {
        super(Resume.class);
        this.setAttribute("previd", id);
        this.setAttribute("h", sequence);
    }
}
