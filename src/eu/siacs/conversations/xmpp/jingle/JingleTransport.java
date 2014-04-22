package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.xml.Element;

public abstract class JingleTransport {
	public abstract void connect(final OnTransportConnected callback);
	public abstract void receive(final JingleFile file, final OnFileTransmitted callback);
	public abstract void send(final JingleFile file, final OnFileTransmitted callback);
}
