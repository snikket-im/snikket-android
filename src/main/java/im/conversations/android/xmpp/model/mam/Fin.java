package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Fin extends Extension {

    public Fin() {
        super(Fin.class);
    }

    public boolean isComplete() {
        return this.getAttributeAsBoolean("complete");
    }
}
