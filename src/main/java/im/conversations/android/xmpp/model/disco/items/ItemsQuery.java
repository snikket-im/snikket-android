package im.conversations.android.xmpp.model.disco.items;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query")
public class ItemsQuery extends Extension {
    public ItemsQuery() {
        super(ItemsQuery.class);
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public String getNode() {
        return this.getAttribute("node");
    }
}
