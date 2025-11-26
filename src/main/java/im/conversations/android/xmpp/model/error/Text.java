package im.conversations.android.xmpp.model.error;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.STANZAS)
public class Text extends Extension {

    public Text() {
        super(Text.class);
    }
}
