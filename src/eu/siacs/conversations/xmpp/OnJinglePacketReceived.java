package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.stanzas.jingle.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	public void onJinglePacketReceived(Account account, JinglePacket packet);
}
