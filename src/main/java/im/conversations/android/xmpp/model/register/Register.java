package im.conversations.android.xmpp.model.register;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "query")
public class Register extends Extension {

    public Register() {
        super(Register.class);
    }

    public void addUsername(final String username) {
        this.addExtension(new Username()).setContent(username);
    }

    public void addPassword(final String password) {
        this.addExtension(new Password()).setContent(password);
    }
}
