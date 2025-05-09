package eu.siacs.conversations.generator;

import android.text.TextUtils;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import im.conversations.android.xmpp.model.stanza.Presence;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private im.conversations.android.xmpp.model.stanza.Presence subscription(
            String type, Contact contact) {
        im.conversations.android.xmpp.model.stanza.Presence packet =
                new im.conversations.android.xmpp.model.stanza.Presence();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().asBareJid());
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence requestPresenceUpdatesFrom(
            final Contact contact) {
        return requestPresenceUpdatesFrom(contact, null);
    }

    public im.conversations.android.xmpp.model.stanza.Presence requestPresenceUpdatesFrom(
            final Contact contact, final String preAuth) {
        im.conversations.android.xmpp.model.stanza.Presence packet =
                subscription("subscribe", contact);
        String displayName = contact.getAccount().getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            packet.addChild("nick", Namespace.NICK).setContent(displayName);
        }
        if (preAuth != null) {
            packet.addChild("preauth", Namespace.PARS).setAttribute("token", preAuth);
        }
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence stopPresenceUpdatesFrom(
            Contact contact) {
        return subscription("unsubscribe", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence stopPresenceUpdatesTo(
            Contact contact) {
        return subscription("unsubscribed", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence sendPresenceUpdatesTo(
            Contact contact) {
        return subscription("subscribed", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(
            Account account, Presence.Availability status) {
        return selfPresence(account, status, true);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(
            final Account account, final Presence.Availability status, final boolean personal) {
        final var connection = account.getXmppConnection();
        if (connection == null) {
            return new Presence();
        }
        return connection.getManager(PresenceManager.class).getPresence(status, personal);
    }

    public im.conversations.android.xmpp.model.stanza.Presence leave(final MucOptions mucOptions) {
        im.conversations.android.xmpp.model.stanza.Presence presence =
                new im.conversations.android.xmpp.model.stanza.Presence();
        presence.setTo(mucOptions.getSelf().getFullJid());
        presence.setFrom(mucOptions.getAccount().getJid());
        presence.setAttribute("type", "unavailable");
        return presence;
    }

    public im.conversations.android.xmpp.model.stanza.Presence sendOfflinePresence(
            Account account) {
        im.conversations.android.xmpp.model.stanza.Presence packet =
                new im.conversations.android.xmpp.model.stanza.Presence();
        packet.setFrom(account.getJid());
        packet.setAttribute("type", "unavailable");
        return packet;
    }
}
