package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class EnablePacket extends AbstractStanza {

	public EnablePacket() {
		super("enable");
		this.setAttribute("xmlns","urn:xmpp:sm:3");
		this.setAttribute("resume", "true");
	}
	
	public EnablePacket(String xmlns) {
		super("enable");
		this.setAttribute("xmlns",xmlns);
		this.setAttribute("resume", "true");
	}

}
