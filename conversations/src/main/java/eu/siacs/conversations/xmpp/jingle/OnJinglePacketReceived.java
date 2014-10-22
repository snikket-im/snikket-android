package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.PacketReceived;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
	public void onJinglePacketReceived(Account account, JinglePacket packet);
}
