package im.conversations.android.xmpp.model.axolotl;

import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Device extends Extension {

    public Device() {
        super(Device.class);
    }

    public Integer getDeviceId() {
        return Ints.tryParse(Strings.nullToEmpty(this.getAttribute("id")));
    }

    public void setDeviceId(int deviceId) {
        this.setAttribute("id", deviceId);
    }
}
