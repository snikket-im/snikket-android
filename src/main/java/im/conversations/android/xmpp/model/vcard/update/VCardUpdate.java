package im.conversations.android.xmpp.model.vcard.update;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "x")
public class VCardUpdate extends Extension {

    public VCardUpdate() {
        super(VCardUpdate.class);
    }

    public Photo getPhoto() {
        return this.getExtension(Photo.class);
    }

    public String getHash() {
        final var photo = getPhoto();
        return photo == null ? null : photo.getContent();
    }
}
