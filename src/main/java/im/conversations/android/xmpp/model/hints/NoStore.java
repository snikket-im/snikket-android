package im.conversations.android.xmpp.model.hints;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class NoStore extends Extension {

    public NoStore() {
        super(NoStore.class);
    }
}
