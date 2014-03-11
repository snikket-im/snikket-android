package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class ResumePacket extends AbstractStanza {

	public ResumePacket(String id, int sequence) {
		super("resume");
		this.setAttribute("xmlns","urn:xmpp:sm:3");
		this.setAttribute("previd", id);
		this.setAttribute("h", ""+sequence);
	}

}
