package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Metadata extends Extension {

    public Metadata() {
        super(Metadata.class);
    }

    public Start getStart() {
        return this.getExtension(Start.class);
    }

    public End getEnd() {
        return this.getExtension(End.class);
    }
}
