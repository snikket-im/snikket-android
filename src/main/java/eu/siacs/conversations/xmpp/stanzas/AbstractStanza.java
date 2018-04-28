package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import rocks.xmpp.addr.Jid;

public class AbstractStanza extends Element {

	protected AbstractStanza(final String name) {
		super(name);
	}

	public Jid getTo() {
		return getAttributeAsJid("to");
	}

	public Jid getFrom() {
		return getAttributeAsJid("from");
	}

	public void setTo(final Jid to) {
		if (to != null) {
			setAttribute("to", to.toEscapedString());
		}
	}

	public void setFrom(final Jid from) {
		if (from != null) {
			setAttribute("from", from.toEscapedString());
		}
	}

	public boolean fromServer(final Account account) {
		final Jid from = getFrom();
		return from == null
			|| from.equals(Jid.of(account.getServer()))
			|| from.equals(account.getJid().asBareJid())
			|| from.equals(account.getJid());
	}

	public boolean toServer(final Account account) {
		final Jid to = getTo();
		return to == null
			|| to.equals(Jid.of(account.getServer()))
			|| to.equals(account.getJid().asBareJid())
			|| to.equals(account.getJid());
	}

	public boolean fromAccount(final Account account) {
		final Jid from = getFrom();
		return from != null && from.asBareJid().equals(account.getJid().asBareJid());
	}
}
