package im.conversations.android.xmpp.model.ibb;

import im.conversations.android.annotation.XmlElement;

@XmlElement
public class Close extends InBandByteStream {

    public Close() {
        super(Close.class);
    }
}
