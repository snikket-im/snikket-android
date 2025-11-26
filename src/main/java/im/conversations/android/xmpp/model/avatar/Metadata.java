package im.conversations.android.xmpp.model.avatar;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.AVATAR_METADATA)
public class Metadata extends Extension {

    public Metadata() {
        super(Metadata.class);
    }
}
