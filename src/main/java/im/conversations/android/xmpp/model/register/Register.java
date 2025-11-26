package im.conversations.android.xmpp.model.register;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import org.jxmpp.jid.parts.Localpart;

@XmlElement(name = "query")
public class Register extends Extension {

    public Register() {
        super(Register.class);
    }

    public void addUsername(final Localpart username) {
        this.addExtension(new Username()).setContent(username.toString());
    }

    public void addPassword(final String password) {
        this.addExtension(new Password()).setContent(password);
    }
}
