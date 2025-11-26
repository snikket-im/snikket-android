package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Encrypted extends Extension {

    public Encrypted() {
        super(Encrypted.class);
    }

    public boolean hasPayload() {
        return hasExtension(Payload.class);
    }

    public Header getHeader() {
        return getExtension(Header.class);
    }

    public Payload getPayload() {
        return getExtension(Payload.class);
    }
}
