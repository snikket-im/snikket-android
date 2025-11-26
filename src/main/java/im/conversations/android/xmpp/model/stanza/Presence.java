package im.conversations.android.xmpp.model.stanza;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;

@XmlElement
public class Presence extends Stanza implements EntityCapabilities {

    public Presence() {
        super(Presence.class);
    }
}
