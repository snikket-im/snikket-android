package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnTLSExceptionReceived {
	public void onTLSExceptionReceived(String fingerprint, Account account);
}
