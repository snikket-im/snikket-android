package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.xml.Element;

public class PresencePacket extends Element {
	private PresencePacket(String name) {
		super("presence");
	}
	
	public PresencePacket() {
		super("presence");
	}
}
