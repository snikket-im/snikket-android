package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.pubsub.Items;
import java.util.Collection;

@XmlElement
public class Event extends Extension {

    public Event() {
        super(Event.class);
    }

    public Items getItems() {
        return this.getExtension(ItemsWrapper.class);
    }

    public Purge getPurge() {
        return this.getExtension(Purge.class);
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
    }
}
