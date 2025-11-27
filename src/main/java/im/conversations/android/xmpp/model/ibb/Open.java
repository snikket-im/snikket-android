package im.conversations.android.xmpp.model.ibb;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Open extends InBandByteStream {

    public Open() {
        super(Open.class);
    }
}
