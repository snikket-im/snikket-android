package eu.siacs.conversations.xmpp.manager;

import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.pep.Avatar;
import im.conversations.android.xmpp.model.avatar.Metadata;
import im.conversations.android.xmpp.model.pubsub.Items;

public class AvatarManager extends AbstractManager {

    private final XmppConnectionService service;

    public AvatarManager(final XmppConnectionService service, XmppConnection connection) {
        super(service.getApplicationContext(), connection);
        this.service = service;
    }

    public void handleItems(final Jid from, final Items items) {
        final var account = getAccount();
        // TODO support retract
        final var entry = items.getFirstItemWithId(Metadata.class);
        final var avatar =
                entry == null ? null : Avatar.parseMetadata(entry.getKey(), entry.getValue());
        if (avatar != null) {
            avatar.owner = from.asBareJid();
            if (service.getFileBackend().isAvatarCached(avatar)) {
                if (account.getJid().asBareJid().equals(from)) {
                    if (account.setAvatar(avatar.getFilename())) {
                        service.databaseBackend.updateAccount(account);
                        service.notifyAccountAvatarHasChanged(account);
                    }
                    service.getAvatarService().clear(account);
                    service.updateConversationUi();
                    service.updateAccountUi();
                } else {
                    final Contact contact = account.getRoster().getContact(from);
                    if (contact.setAvatar(avatar)) {
                        connection.getManager(RosterManager.class).writeToDatabaseAsync();
                        service.getAvatarService().clear(contact);
                        service.updateConversationUi();
                        service.updateRosterUi();
                    }
                }
            } else if (service.isDataSaverDisabled()) {
                service.fetchAvatar(account, avatar);
            }
        }
    }

    public void handleDelete(final Jid from) {
        final var account = getAccount();
        final boolean isAccount = account.getJid().asBareJid().equals(from);
        if (isAccount) {
            account.setAvatar(null);
            getDatabase().updateAccount(account);
            service.getAvatarService().clear(account);
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": deleted avatar metadata node");
        }
    }
}
