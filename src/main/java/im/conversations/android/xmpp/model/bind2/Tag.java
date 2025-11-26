package im.conversations.android.xmpp.model.bind2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Tag extends Extension {

    public Tag() {
        super(Tag.class);
    }

    public Tag(final String tag) {
        this();
        setContent(tag);
    }
}
