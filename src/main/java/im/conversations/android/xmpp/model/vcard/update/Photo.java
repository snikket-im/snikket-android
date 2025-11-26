package im.conversations.android.xmpp.model.vcard.update;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Photo extends Extension {

    public Photo() {
        super(Photo.class);
    }
}
