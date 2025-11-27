package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "PHOTO")
public class Photo extends Extension {
    public Photo() {
        super(Photo.class);
    }

    public BinaryValue getBinaryValue() {
        return this.getExtension(BinaryValue.class);
    }

    public void setType(final String value) {
        final var type = this.addExtension(new Type());
        type.setContent(value);
    }
}
