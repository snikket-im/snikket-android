package eu.siacs.conversations.entities;

import eu.siacs.conversations.xmpp.jid.Jid;

public interface Blockable {
	boolean isBlocked();
	boolean isDomainBlocked();
	Jid getBlockedJid();
	Jid getJid();
	Account getAccount();
}
