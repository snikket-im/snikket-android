package im.conversations.android.xmpp.model.hints;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class NoCopy extends Extension {
    public NoCopy() {
        super(NoCopy.class);
    }
}
