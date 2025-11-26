package eu.siacs.conversations.generator;

import android.text.TextUtils;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private im.conversations.android.xmpp.model.stanza.Presence subscription(String type, Contact contact) {
        im.conversations.android.xmpp.model.stanza.Presence packet = new im.conversations.android.xmpp.model.stanza.Presence();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().asBareJid());
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence requestPresenceUpdatesFrom(final Contact contact) {
        return requestPresenceUpdatesFrom(contact, null);
    }

    public im.conversations.android.xmpp.model.stanza.Presence requestPresenceUpdatesFrom(final Contact contact, final String preAuth) {
        im.conversations.android.xmpp.model.stanza.Presence packet = subscription("subscribe", contact);
        String displayName = contact.getAccount().getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            packet.addChild("nick", Namespace.NICK).setContent(displayName);
        }
        if (preAuth != null) {
            packet.addChild("preauth", Namespace.PARS).setAttribute("token", preAuth);
        }
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence stopPresenceUpdatesFrom(Contact contact) {
        return subscription("unsubscribe", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence stopPresenceUpdatesTo(Contact contact) {
        return subscription("unsubscribed", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence sendPresenceUpdatesTo(Contact contact) {
        return subscription("subscribed", contact);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(Account account, Presence.Status status) {
        return selfPresence(account, status, true);
    }

    public im.conversations.android.xmpp.model.stanza.Presence selfPresence(final Account account, final Presence.Status status, final boolean personal) {
        final im.conversations.android.xmpp.model.stanza.Presence packet = new im.conversations.android.xmpp.model.stanza.Presence();
        if (personal) {
            final String sig = account.getPgpSignature();
            final String message = account.getPresenceStatusMessage();
            if (status.toShowString() != null) {
                packet.addChild("show").setContent(status.toShowString());
            }
            if (!TextUtils.isEmpty(message)) {
                packet.addChild(new Element("status").setContent(message));
            }
            if (sig != null && mXmppConnectionService.getPgpEngine() != null) {
                packet.addChild("x", "jabber:x:signed").setContent(sig);
            }
        }
        final String capHash = getCapHash(account);
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://conversations.im");
            cap.setAttribute("ver", capHash);
        }
        return packet;
    }

    public im.conversations.android.xmpp.model.stanza.Presence leave(final MucOptions mucOptions) {
        im.conversations.android.xmpp.model.stanza.Presence presence = new im.conversations.android.xmpp.model.stanza.Presence();
        presence.setTo(mucOptions.getSelf().getFullJid());
        presence.setFrom(mucOptions.getAccount().getJid());
        presence.setAttribute("type", "unavailable");
        return presence;
    }

    public im.conversations.android.xmpp.model.stanza.Presence sendOfflinePresence(Account account) {
        im.conversations.android.xmpp.model.stanza.Presence packet = new im.conversations.android.xmpp.model.stanza.Presence();
        packet.setFrom(account.getJid());
        packet.setAttribute("type", "unavailable");
        return packet;
    }
}
