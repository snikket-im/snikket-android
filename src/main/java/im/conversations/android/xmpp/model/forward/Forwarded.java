package im.conversations.android.xmpp.model.forward;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.stanza.Message;

@XmlElement(namespace = Namespace.FORWARD)
public class Forwarded extends Extension {

    public Forwarded() {
        super(Forwarded.class);
    }

    public Message getMessage() {
        return this.getExtension(Message.class);
    }
}
