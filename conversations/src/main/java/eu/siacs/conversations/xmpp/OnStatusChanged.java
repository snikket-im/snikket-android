package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnStatusChanged {
	public void onStatusChanged(Account account);
}
