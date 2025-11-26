package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Query extends Extension {

    public Query() {
        super(Query.class);
    }

    public void setQueryId(final String id) {
        this.setAttribute("queryid", id);
    }
}
