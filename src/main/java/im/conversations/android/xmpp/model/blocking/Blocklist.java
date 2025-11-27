package im.conversations.android.xmpp.model.blocking;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement
public class Blocklist extends Extension {
    public Blocklist() {
        super(Blocklist.class);
    }

    public Collection<Item> getItems() {
        return this.getExtensions(Item.class);
    }
}
