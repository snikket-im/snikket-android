package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Device extends Extension {

    public Device() {
        super(Device.class);
    }

    public Device(final String device) {
        this();
        this.setContent(device);
    }
}
