package im.conversations.android.xmpp.model.socks5;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Query extends Extension {

    public Query() {
        super(Query.class);
    }

    public StreamHost getStreamHost() {
        return this.getExtension(StreamHost.class);
    }

    public void setSid(final String streamId) {
        this.setAttribute("sid", streamId);
    }
}
