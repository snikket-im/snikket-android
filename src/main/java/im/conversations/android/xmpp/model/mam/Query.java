package im.conversations.android.xmpp.model.mam;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;
import im.conversations.android.xmpp.model.rsm.Set;
import java.util.Map;

@XmlElement
public class Query extends Extension {

    public Query() {
        super(Query.class);
    }

    public void setQueryId(final String id) {
        this.setAttribute("queryid", id);
    }

    public void setFilter(final Map<String, Object> filter) {
        this.addExtension(Data.of(filter, Namespace.MESSAGE_ARCHIVE_MANAGEMENT));
    }

    public void setResultSet(final Set resultSet) {
        this.addExtension(resultSet);
    }
}
