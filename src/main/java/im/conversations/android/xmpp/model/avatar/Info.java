package im.conversations.android.xmpp.model.avatar;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.AVATAR_METADATA)
public class Info extends Extension {

    public Info() {
        super(Info.class);
    }

    public long getHeight() {
        return this.getLongAttribute("height");
    }

    public long getWidth() {
        return this.getLongAttribute("width");
    }

    public long getBytes() {
        return this.getLongAttribute("bytes");
    }

    public String getType() {
        return this.getAttribute("type");
    }

    public String getUrl() {
        return this.getAttribute("url");
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
