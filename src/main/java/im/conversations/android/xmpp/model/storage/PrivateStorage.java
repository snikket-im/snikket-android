package im.conversations.android.xmpp.model.storage;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query", namespace = Namespace.PRIVATE_XML_STORAGE)
public class PrivateStorage extends Extension {

    public PrivateStorage() {
        super(PrivateStorage.class);
    }
}
