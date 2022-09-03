package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class ResumePacket extends AbstractStanza {

	public ResumePacket(final String id, final int sequence) {
		super("resume");
		this.setAttribute("xmlns", Namespace.STREAM_MANAGEMENT);
		this.setAttribute("previd", id);
		this.setAttribute("h", Integer.toString(sequence));
	}

}
