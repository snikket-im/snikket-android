package im.conversations.android.xmpp.model.cb;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class ChannelBinding extends Extension {

    public ChannelBinding() {
        super(ChannelBinding.class);
    }

    public String getType() {
        return this.getAttribute("type");
    }
}
