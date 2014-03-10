package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class AckPacket extends AbstractStanza {

	public AckPacket(int sequence) {
		super("a");
		this.setAttribute("xmlns","urn:xmpp:sm:3");
		this.setAttribute("h", ""+sequence);
	}

}
