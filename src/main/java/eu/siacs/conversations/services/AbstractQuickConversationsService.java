package eu.siacs.conversations.services;

import eu.siacs.conversations.BuildConfig;

public abstract class AbstractQuickConversationsService {

    protected final XmppConnectionService service;

    public AbstractQuickConversationsService(XmppConnectionService service) {
        this.service = service;
    }

    public abstract void considerSync();

    public static boolean isQuicksy() {
        return "quicksy".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isConversations() {
        return "conversations".equals(BuildConfig.FLAVOR_mode);
    }

    public abstract void signalAccountStateChange();
}
