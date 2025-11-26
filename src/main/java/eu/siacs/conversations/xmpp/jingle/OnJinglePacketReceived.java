package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.Account;
import im.conversations.android.xmpp.model.stanza.Iq;

public interface OnJinglePacketReceived {
	void onJinglePacketReceived(Account account, Iq packet);
}
