package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;

public class MessagePacket extends Element {
	private MessagePacket(String name) {
		super(name);
	}
	
	public MessagePacket() {
		super("message");
	}
}
