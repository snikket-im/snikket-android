package im.conversations.android.xmpp.model.reactions;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Reaction extends Extension {

    public Reaction() {
        super(Reaction.class);
    }

    public Reaction(final String reaction) {
        this();
        setContent(reaction);
    }
}
