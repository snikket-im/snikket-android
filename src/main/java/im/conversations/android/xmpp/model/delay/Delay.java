package im.conversations.android.xmpp.model.delay;

import com.google.common.base.Strings;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.Timestamps;
import im.conversations.android.xmpp.model.Extension;
import java.text.ParseException;
import java.time.Instant;

@XmlElement(namespace = Namespace.DELAY)
public class Delay extends Extension {

    public Delay() {
        super(Delay.class);
    }

    public Instant getStamp() {
        final var stamp = this.getAttribute("stamp");
        if (Strings.isNullOrEmpty(stamp)) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Timestamps.parse(stamp));
        } catch (final IllegalArgumentException | ParseException e) {
            return null;
        }
    }
}
