package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnMessagePacketReceived extends PacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}
