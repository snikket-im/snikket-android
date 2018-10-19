package eu.siacs.conversations.services;

import eu.siacs.conversations.services.XmppConnectionService;

public class QuickConversationsService {

    private final XmppConnectionService service;

    QuickConversationsService(XmppConnectionService xmppConnectionService) {
        this.service = xmppConnectionService;
    }
}