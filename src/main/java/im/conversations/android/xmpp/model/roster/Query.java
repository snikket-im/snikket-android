package im.conversations.android.xmpp.model.roster;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement(name = "query", namespace = Namespace.ROSTER)
public class Query extends Extension {

    public Query() {
        super(Query.class);
    }

    public void setVersion(final String rosterVersion) {
        this.setAttribute("ver", rosterVersion);
    }

    public String getVersion() {
        return this.getAttribute("ver");
    }

    public Collection<Item> getItems() {
        return this.getExtensions(Item.class);
    }
}
