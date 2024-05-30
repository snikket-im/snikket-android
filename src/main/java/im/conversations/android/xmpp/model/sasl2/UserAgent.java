package im.conversations.android.xmpp.model.sasl2;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class UserAgent extends Extension {

    public UserAgent() {
        super(UserAgent.class);
    }

    public UserAgent(final String userAgentId) {
        this();
        this.setAttribute("id", userAgentId);
    }

    public void setSoftware(final String software) {
        this.addExtension(new Software(software));
    }

    public void setDevice(final String device) {
        this.addExtension(new Device(device));
    }
}
