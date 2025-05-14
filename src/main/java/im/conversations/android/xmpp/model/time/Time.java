package im.conversations.android.xmpp.model.time;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Time extends Extension {

    public Time() {
        super(Time.class);
    }

    public void setTimeZoneOffset(final String tzo) {
        this.addExtension(new TimeZoneOffset()).setContent(tzo);
    }

    public void setUniversalTime(final String utc) {
        this.addExtension(new UniversalTime()).setContent(utc);
    }
}
