package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;

public class PresencePacket extends Element {
	private PresencePacket(String name) {
		super("presence");
	}
	
	public PresencePacket() {
		super("presence");
	}
}
