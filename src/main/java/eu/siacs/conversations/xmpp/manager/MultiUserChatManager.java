package eu.siacs.conversations.xmpp.manager;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;

public class MultiUserChatManager extends AbstractManager {

    private final XmppConnectionService service;

    public MultiUserChatManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }
}
