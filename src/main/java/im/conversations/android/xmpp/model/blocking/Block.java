package im.conversations.android.xmpp.model.blocking;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Block extends Extension {

    public Block() {
        super(Block.class);
    }
}
