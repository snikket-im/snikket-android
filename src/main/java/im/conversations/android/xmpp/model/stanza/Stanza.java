package im.conversations.android.xmpp.model.stanza;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.InvalidJid;
import eu.siacs.conversations.xmpp.Jid;

import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.error.Error;

public abstract class Stanza extends StreamElement {

    protected Stanza(final Class<? extends Stanza> clazz) {
        super(clazz);
    }

    public Jid getTo() {
        return this.getAttributeAsJid("to");
    }

    public Jid getFrom() {
        return this.getAttributeAsJid("from");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }

    public void setFrom(final Jid from) {
        this.setAttribute("from", from);
    }

    public void setTo(final Jid to) {
        this.setAttribute("to", to);
    }

    public Error getError() {
        return this.getExtension(Error.class);
    }

    public boolean isInvalid() {
        final var to = getTo();
        final var from = getFrom();
        if (to instanceof InvalidJid || from instanceof InvalidJid) {
            return true;
        }
        return false;
    }

    public boolean fromServer(final Account account) {
        final Jid from = getFrom();
        return from == null
                || from.equals(account.getDomain())
                || from.equals(account.getJid().asBareJid())
                || from.equals(account.getJid());
    }

    public boolean toServer(final Account account) {
        final Jid to = getTo();
        return to == null
                || to.equals(account.getDomain())
                || to.equals(account.getJid().asBareJid())
                || to.equals(account.getJid());
    }

    public boolean fromAccount(final Account account) {
        final Jid from = getFrom();
        return from != null && from.asBareJid().equals(account.getJid().asBareJid());
    }
}
