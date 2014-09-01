package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class ResumePacket extends AbstractStanza {

	public ResumePacket(String id, int sequence, int smVersion) {
		super("resume");
		this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
		this.setAttribute("previd", id);
		this.setAttribute("h", Integer.toString(sequence));
	}

}
