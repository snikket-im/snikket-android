package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnPresencePacketReceived extends PacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
