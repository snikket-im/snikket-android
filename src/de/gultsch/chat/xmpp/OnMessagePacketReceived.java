package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnMessagePacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
