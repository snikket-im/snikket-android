package im.conversations.android.xmpp.model.idle;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@XmlElement
public class Idle extends Extension {

    public Idle() {
        super(Idle.class);
    }

    public Idle(final Instant instant) {
        this();
        this.setAttribute("since", instant);
    }

    private void setAttribute(final String name, final Instant instant) {
        this.setAttribute(name, DateTimeFormatter.ISO_INSTANT.format(instant));
    }

    public Instant getSince() {
        return this.getAttributeAsInstant("since");
    }
}
