package im.conversations.android.xmpp.model.pubsub.event;

import im.conversations.android.xmpp.model.Extension;

public abstract class Action extends Extension {

    public Action(Class<? extends Action> clazz) {
        super(clazz);
    }

    public String getNode() {
        return this.getAttribute("node");
    }
}
