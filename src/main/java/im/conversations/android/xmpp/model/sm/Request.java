package im.conversations.android.xmpp.model.sm;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement(name = "r")
public class Request extends StreamElement {

    public Request() {
        super(Request.class);
    }
}
