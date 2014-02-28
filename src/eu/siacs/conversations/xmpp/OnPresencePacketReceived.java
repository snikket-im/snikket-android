package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnPresencePacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
