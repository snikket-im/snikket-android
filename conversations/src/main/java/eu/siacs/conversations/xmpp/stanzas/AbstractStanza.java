package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;

public class AbstractStanza extends Element {

	protected AbstractStanza(String name) {
		super(name);
	}

	public String getTo() {
		return getAttribute("to");
	}

	public String getFrom() {
		return getAttribute("from");
	}

	public String getId() {
		return this.getAttribute("id");
	}

	public void setTo(String to) {
		setAttribute("to", to);
	}

	public void setFrom(String from) {
		setAttribute("from", from);
	}

	public void setId(String id) {
		setAttribute("id", id);
	}
}
