package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;

public class Reason extends Element {
	private Reason(String name) {
		super(name);
	}

	public Reason() {
		super("reason");
	}
}
