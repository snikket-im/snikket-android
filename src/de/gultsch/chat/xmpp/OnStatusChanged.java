package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnStatusChanged {
	public void onStatusChanged(Account account);
}
