package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class EnablePacket extends AbstractStanza {

	public EnablePacket() {
		super("enable");
		this.setAttribute("xmlns", Namespace.STREAM_MANAGEMENT);
		this.setAttribute("resume", "true");
	}

}
