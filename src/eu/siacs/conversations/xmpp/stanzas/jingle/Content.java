package eu.siacs.conversations.xmpp.stanzas.jingle;

import eu.siacs.conversations.xml.Element;

public class Content extends Element {
	private Content(String name) {
		super(name);
	}
	
	public Content() {
		super("content");
	}
}
