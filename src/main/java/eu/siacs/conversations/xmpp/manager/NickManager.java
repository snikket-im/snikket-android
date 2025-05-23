package eu.siacs.conversations.xmpp.manager;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.NodeConfiguration;
import im.conversations.android.xmpp.model.nick.Nick;
import im.conversations.android.xmpp.model.pubsub.Items;

public class NickManager extends AbstractManager {

    private final XmppConnectionService service;

    public NickManager(final XmppConnectionService service, final XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void handleItems(final Jid from, final Items items) {
        final var item = items.getFirstItem(Nick.class);
        final var nick = item == null ? null : item.getContent();
        if (from == null || Strings.isNullOrEmpty(nick)) {
            return;
        }
        setNick(from, nick);
    }

    private void setNick(final Jid user, final String nick) {
        final var account = getAccount();
        if (user.asBareJid().equals(account.getJid().asBareJid())) {
            account.setDisplayName(nick);
            if (QuickConversationsService.isQuicksy()) {
                service.getAvatarService().clear(account);
            }
            service.checkMucRequiresRename();
        } else {
            final Contact contact = account.getRoster().getContact(user);
            if (contact.setPresenceName(nick)) {
                connection.getManager(RosterManager.class).writeToDatabaseAsync();
                service.getAvatarService().clear(contact);
            }
        }
        service.updateConversationUi();
        service.updateAccountUi();
    }

    public ListenableFuture<Void> publish(final String name) {
        if (Strings.isNullOrEmpty(name)) {
            return getManager(PepManager.class).delete(Namespace.NICK);
        } else {
            return getManager(PepManager.class)
                    .publishSingleton(new Nick(name), NodeConfiguration.PRESENCE);
        }
    }

    public void handleDelete(final Jid from) {
        this.setNick(from, null);
    }
}
