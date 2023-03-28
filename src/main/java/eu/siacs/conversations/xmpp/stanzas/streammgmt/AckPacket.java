package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class AckPacket extends AbstractStanza {

	public AckPacket(final int sequence) {
		super("a");
		this.setAttribute("xmlns", Namespace.STREAM_MANAGEMENT);
		this.setAttribute("h", Integer.toString(sequence));
	}

}
