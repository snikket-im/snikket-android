package im.conversations.android.xmpp.model.sm;

import com.google.common.base.Optional;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement(name = "a")
public class Ack extends StreamElement {

    public Ack() {
        super(Ack.class);
    }

    public Ack(final int sequence) {
        super(Ack.class);
        this.setAttribute("h", sequence);
    }

    public Optional<Integer> getHandled() {
        return this.getOptionalIntAttribute("h");
    }
}
