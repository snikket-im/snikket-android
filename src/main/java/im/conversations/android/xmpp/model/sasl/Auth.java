package im.conversations.android.xmpp.model.sasl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Auth extends StreamElement {

    public Auth() {
        super(Auth.class);
    }
}
