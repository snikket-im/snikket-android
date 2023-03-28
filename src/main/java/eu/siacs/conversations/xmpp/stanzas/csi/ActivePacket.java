package eu.siacs.conversations.xmpp.stanzas.csi;

import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public class ActivePacket extends AbstractStanza {
	public ActivePacket() {
		super("active");
		setAttribute("xmlns", Namespace.CSI);
	}
}
