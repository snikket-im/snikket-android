package im.conversations.android.xmpp.model.muc.admin;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;

@XmlElement(name = "query")
public class MucAdmin extends Extension {

    public MucAdmin() {
        super(MucAdmin.class);
    }

    public Collection<Item> getItems() {
        return this.getExtensions(Item.class);
    }
}
