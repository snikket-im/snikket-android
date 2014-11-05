package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

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

	public void setTo(final Jid to) {
		setAttribute("to", to.toString());
	}

	public void setFrom(final Jid from) {
		setAttribute("from", from.toString());
	}

	public void setId(final String id) {
		setAttribute("id", id);
	}
}
