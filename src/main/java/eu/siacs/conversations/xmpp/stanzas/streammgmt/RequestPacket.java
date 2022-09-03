package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class RequestPacket extends AbstractStanza {

	public RequestPacket() {
		super("r");
		this.setAttribute("xmlns", Namespace.STREAM_MANAGEMENT);
	}

}
