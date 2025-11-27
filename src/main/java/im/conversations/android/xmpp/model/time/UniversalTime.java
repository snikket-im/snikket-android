package im.conversations.android.xmpp.model.time;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "utc")
public class UniversalTime extends Extension {

    public UniversalTime() {
        super(UniversalTime.class);
    }
}
