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
        final var hash = photo == null ? null : photo.getContent();
        return isValidSHA1(hash) ? hash : null;
    }

    public static boolean isValidSHA1(final String s) {
        return s != null && s.matches("[a-fA-F0-9]{40}");
    }
}
