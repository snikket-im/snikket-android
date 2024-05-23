package im.conversations.android.xmpp.model.fast;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Token extends Extension {

    public Token() {
        super(Token.class);
    }
}
