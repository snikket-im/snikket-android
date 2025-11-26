package im.conversations.android.xmpp.model.bind;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Resource extends Extension {
    public Resource() {
        super(Resource.class);
    }

    public Resource(final String resource) {
        this();
        this.setContent(resource);
    }
}
