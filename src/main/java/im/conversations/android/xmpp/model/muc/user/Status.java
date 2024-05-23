package im.conversations.android.xmpp.model.muc.user;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Status extends Extension {

    public Status() {
        super(Status.class);
    }

    public Integer getCode() {
        return this.getOptionalIntAttribute("code").orNull();
    }
}
