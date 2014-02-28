package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnMessagePacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
