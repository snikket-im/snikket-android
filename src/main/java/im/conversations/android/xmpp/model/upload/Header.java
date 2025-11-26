package im.conversations.android.xmpp.model.upload;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Header extends Extension {

    public Header() {
        super(Header.class);
    }

    public String getHeaderName() {
        return this.getAttribute("name");
    }
}
