package im.conversations.android.xmpp.model.sm;

import com.google.common.base.Optional;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Resumed extends StreamElement {

    public Resumed() {
        super(Resumed.class);
    }

    public Optional<Integer> getHandled() {
        return this.getOptionalIntAttribute("h");
    }
}
