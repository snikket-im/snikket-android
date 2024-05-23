package im.conversations.android.xmpp.model.disco.items;

import androidx.annotation.Nullable;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Item extends Extension {
    public Item() {
        super(Item.class);
    }

    public Jid getJid() {
        return getAttributeAsJid("jid");
    }

    public @Nullable String getNode() {
        return this.getAttribute("node");
    }
}
