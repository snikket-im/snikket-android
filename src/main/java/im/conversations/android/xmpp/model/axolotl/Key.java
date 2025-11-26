package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Key extends Extension implements ByteContent {

    public Key() {
        super(Key.class);
    }

    public void setIsPreKey(boolean isPreKey) {
        this.setAttribute("prekey", isPreKey);
    }

    public boolean isPreKey() {
        return this.getAttributeAsBoolean("prekey");
    }

    public void setRemoteDeviceId(final int remoteDeviceId) {
        this.setAttribute("rid", remoteDeviceId);
    }

    public Integer getRemoteDeviceId() {
        return getOptionalIntAttribute("rid").orNull();
    }
}
