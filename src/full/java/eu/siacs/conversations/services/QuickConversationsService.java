package eu.siacs.conversations.services;

public class QuickConversationsService {

    private final XmppConnectionService service;

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        this.service = xmppConnectionService;
    }

    public static boolean isQuicksy() {
        return false;
    }
}