package im.conversations.android.xmpp.model.jabber;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Body extends Extension {

    public Body() {
        super(Body.class);
    }

    public Body(final String content) {
        this();
        setContent(content);
    }

    public String getLang() {
        return this.getAttribute("xml:lang");
    }
}
