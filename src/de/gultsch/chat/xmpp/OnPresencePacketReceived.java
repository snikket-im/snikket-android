package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnPresencePacketReceived {
	public void onPresencePacketReceived(Account account, PresencePacket packet);
}
