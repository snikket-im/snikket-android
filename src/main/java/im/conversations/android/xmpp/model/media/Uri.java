package im.conversations.android.xmpp.model.media;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Uri extends Extension {

    public Uri() {
        super(Uri.class);
    }
}
