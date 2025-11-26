package im.conversations.android.xmpp.model.sasl;

import eu.siacs.conversations.crypto.sasl.SaslMechanism;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.AuthenticationRequest;
import im.conversations.android.xmpp.model.StreamElement;

@XmlElement
public class Auth extends AuthenticationRequest {

    public Auth() {
        super(Auth.class);
    }

    @Override
    public void setMechanism(final SaslMechanism mechanism) {
        this.setAttribute("mechanism", mechanism.getMechanism());
    }
}
