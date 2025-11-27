package im.conversations.android.xmpp.model.forward;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.delay.Delay;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;

@XmlElement(namespace = Namespace.FORWARD)
public class Forwarded extends Extension {

    public Forwarded() {
        super(Forwarded.class);
    }

    public Message getMessage() {
        return this.getOnlyExtension(Message.class);
    }

    public Delay getDelay() {
        return this.getOnlyExtension(Delay.class);
    }

    public Instant getStamp() {
        final var delay = getDelay();
        return delay == null ? null : delay.getStamp();
    }
}
