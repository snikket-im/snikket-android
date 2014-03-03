package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnIqPacketReceived extends PacketReceived {
	public void onIqPacketReceived(Account account, IqPacket packet);
}
