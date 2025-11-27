package im.conversations.android.xmpp.model.time;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "tzo")
public class TimeZoneOffset extends Extension {

    public TimeZoneOffset() {
        super(TimeZoneOffset.class);
    }
}
