package im.conversations.android.xmpp.model.token;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement
public class Register extends StreamFeature {

    public Register() {
        super(Register.class);
    }
}
