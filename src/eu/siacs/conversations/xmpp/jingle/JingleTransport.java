package eu.siacs.conversations.xmpp.jingle;

public abstract class JingleTransport {
	public abstract void receive(final JingleFile file, final OnFileTransmitted callback);
	public abstract void send(final JingleFile file, final OnFileTransmitted callback);
}
