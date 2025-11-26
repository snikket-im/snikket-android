package im.conversations.android.xmpp.model.pars;

import im.conversations.android.annotation.XmlElement;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.PARS)
public class PreAuth extends Extension {

    public PreAuth() {
        super(PreAuth.class);
    }

    public void setToken(final String token) {
        this.setAttribute("token", token);
    }
}
