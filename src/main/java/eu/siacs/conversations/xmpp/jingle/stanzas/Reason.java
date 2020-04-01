package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;

public class Reason extends Element {

	public Reason() {
		super("reason");
	}

	public static Reason upgrade(final Element element) {
		Preconditions.checkArgument("reason".equals(element.getName()));
		final Reason reason = new Reason();
		reason.setAttributes(element.getAttributes());
		reason.setChildren(element.getChildren());
		return reason;
	}
}
