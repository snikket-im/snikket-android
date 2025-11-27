package im.conversations.android.xmpp.model.delay;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.Instant;

@XmlElement(namespace = Namespace.DELAY)
public class Delay extends Extension {

    public Delay() {
        super(Delay.class);
    }

    public Instant getStamp() {
        return this.getAttributeAsInstant("stamp");
    }
}
