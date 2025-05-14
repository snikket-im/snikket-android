package im.conversations.android.xmpp.model.ibb;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;

@XmlElement
public class Data extends InBandByteStream implements ByteContent {

    public Data() {
        super(Data.class);
    }
}
