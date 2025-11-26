package im.conversations.android.xmpp.model.pubsub;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.pubsub.event.Retract;
import java.util.Collection;

@XmlElement(name = "pubsub")
public class PubSub extends Extension {

    public PubSub() {
        super(PubSub.class);
    }

    public Items getItems() {
        return this.getExtension(ItemsWrapper.class);
    }

    @XmlElement(name = "items")
    public static class ItemsWrapper extends Extension implements Items {

        public ItemsWrapper() {
            super(ItemsWrapper.class);
        }

        public String getNode() {
            return this.getAttribute("node");
        }

        public Collection<? extends im.conversations.android.xmpp.model.pubsub.Item> getItems() {
            return this.getExtensions(Item.class);
        }

        public Collection<Retract> getRetractions() {
            return this.getExtensions(Retract.class);
        }

        public void setNode(String node) {
            this.setAttribute("node", node);
        }

        public void setMaxItems(final int maxItems) {
            this.setAttribute("max_items", maxItems);
        }
    }

    @XmlElement(name = "item")
    public static class Item extends Extension
            implements im.conversations.android.xmpp.model.pubsub.Item {

        public Item() {
            super(Item.class);
        }

        @Override
        public String getId() {
            return this.getAttribute("id");
        }

        public void setId(String itemId) {
            this.setAttribute("id", itemId);
        }
    }
}
