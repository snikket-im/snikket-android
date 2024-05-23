package im.conversations.android.xmpp.model.bookmark;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Conference extends Extension {

    public Conference() {
        super(Conference.class);
    }

    public boolean isAutoJoin() {
        return this.getAttributeAsBoolean("autojoin");
    }

    public String getConferenceName() {
        return this.getAttribute("name");
    }

    public void setAutoJoin(boolean autoJoin) {
        setAttribute("autojoin", autoJoin);
    }

    public Nick getNick() {
        return this.getExtension(Nick.class);
    }

    public Extensions getExtensions() {
        return this.getExtension(Extensions.class);
    }
}
