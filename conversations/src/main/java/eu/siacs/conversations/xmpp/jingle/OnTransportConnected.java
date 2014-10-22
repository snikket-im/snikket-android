package eu.siacs.conversations.xmpp.jingle;

public interface OnTransportConnected {
	public void failed();

	public void established();
}
