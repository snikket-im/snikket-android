package im.conversations.android.xmpp.model.fast;

import eu.siacs.conversations.crypto.sasl.HashedToken;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class RequestToken extends Extension {
    public RequestToken() {
        super(RequestToken.class);
    }

    public RequestToken(final HashedToken.Mechanism mechanism) {
        this();
        this.setAttribute("mechanism", mechanism.name());
    }
}
