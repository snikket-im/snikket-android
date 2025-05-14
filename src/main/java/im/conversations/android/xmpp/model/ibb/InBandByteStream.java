package im.conversations.android.xmpp.model.ibb;

import im.conversations.android.xmpp.model.Extension;

public abstract class InBandByteStream extends Extension {

    public InBandByteStream(Class<? extends InBandByteStream> clazz) {
        super(clazz);
    }

    public String getSid() {
        return this.getAttribute("sid");
    }
}
