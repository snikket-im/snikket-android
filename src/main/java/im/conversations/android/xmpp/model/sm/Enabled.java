package im.conversations.android.xmpp.model.sm;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Enabled extends StreamElement {

    public Enabled() {
        super(Enabled.class);
    }

    public boolean isResume() {
        return this.getAttributeAsBoolean("resume");
    }

    public String getLocation() {
        return this.getAttribute("location");
    }

    public Optional<String> getResumeId() {
        final var id = this.getAttribute("id");
        if (Strings.isNullOrEmpty(id)) {
            return Optional.absent();
        }
        if (isResume()) {
            return Optional.of(id);
        } else {
            return Optional.absent();
        }
    }
}
