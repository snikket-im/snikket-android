package im.conversations.android.xmpp.model.state;

import im.conversations.android.xmpp.model.Extension;

public abstract sealed class ChatStateNotification extends Extension
        permits Active, Composing, Gone, Inactive, Paused {

    protected ChatStateNotification(Class<? extends ChatStateNotification> clazz) {
        super(clazz);
    }
}
