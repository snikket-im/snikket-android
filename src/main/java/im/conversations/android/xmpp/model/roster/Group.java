package im.conversations.android.xmpp.model.roster;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Group extends Extension {

    public Group() {
        super(Group.class);
    }
}
