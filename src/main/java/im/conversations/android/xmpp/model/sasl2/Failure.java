package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationFailure;

@XmlElement
public class Failure extends AuthenticationFailure {

    public Failure() {
        super(Failure.class);
    }
}
