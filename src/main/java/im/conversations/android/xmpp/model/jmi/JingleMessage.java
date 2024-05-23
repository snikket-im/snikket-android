package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.xmpp.model.Extension;

public abstract class JingleMessage extends Extension {

    public JingleMessage(Class<? extends JingleMessage> clazz) {
        super(clazz);
    }

    public String getSessionId() {
        return this.getAttribute("id");
    }
}
