package im.conversations.android.xmpp.model.vcard;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "PHOTO")
public class Photo extends Extension {
    public Photo() {
        super(Photo.class);
    }
}
