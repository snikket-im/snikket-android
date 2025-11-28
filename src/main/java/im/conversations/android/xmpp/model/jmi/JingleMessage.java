package im.conversations.android.xmpp.model.jmi;

import im.conversations.android.xmpp.model.Extension;

public abstract sealed class JingleMessage extends Extension
        permits Accept, Finish, Proceed, Propose, Reject, Retract, Ringing {

    public JingleMessage(Class<? extends JingleMessage> clazz) {
        super(clazz);
    }

    public String getSessionId() {
        return this.getAttribute("id");
    }
}
