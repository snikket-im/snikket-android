package im.conversations.android.xmpp.model.sm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "sm")
public class StreamManagement extends StreamFeature {

    public StreamManagement() {
        super(StreamManagement.class);
    }
}
