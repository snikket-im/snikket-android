package im.conversations.android.xmpp.model.streams;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement(name = "error")
public class StreamError extends StreamElement {

    public StreamError() {
        super(StreamError.class);
    }
}
