package im.conversations.android.xmpp.model.muc.owner;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.data.Data;

@XmlElement(name = "query")
public class MucOwner extends Extension {
    public MucOwner() {
        super(MucOwner.class);
    }

    public Data getConfiguration() {
        return this.getExtension(Data.class);
    }
}
