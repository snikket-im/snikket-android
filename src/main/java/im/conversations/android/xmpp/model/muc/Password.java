package im.conversations.android.xmpp.model.muc;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Password extends Extension {

    public Password() {
        super(Password.class);
    }

    public Password(final String password) {
        this();
        this.setContent(password);
    }
}
