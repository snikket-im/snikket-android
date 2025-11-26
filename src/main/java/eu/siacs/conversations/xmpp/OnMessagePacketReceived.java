package eu.siacs.conversations.xmpp;

import im.conversations.android.xmpp.model.stanza.Message;

public interface OnMessagePacketReceived {
	void onMessagePacketReceived(Message packet);
}
