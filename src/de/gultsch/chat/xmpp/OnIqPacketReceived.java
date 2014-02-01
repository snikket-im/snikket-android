package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnIqPacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}
