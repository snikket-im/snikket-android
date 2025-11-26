package im.conversations.android.xmpp.model.tls;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement(name = "starttls")
public class StartTls extends StreamElement {
    public StartTls() {
        super(StartTls.class);
    }

    public boolean isRequired() {
        return hasExtension(Required.class);
    }
}
