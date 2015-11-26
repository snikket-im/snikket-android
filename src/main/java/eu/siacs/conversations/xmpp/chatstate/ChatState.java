package eu.siacs.conversations.xmpp.chatstate;

import eu.siacs.conversations.xml.Element;

public enum ChatState {

	ACTIVE, INACTIVE, GONE, COMPOSING, PAUSED;

	public static ChatState parse(Element element) {
		final String NAMESPACE = "http://jabber.org/protocol/chatstates";
		if (element.hasChild("active",NAMESPACE)) {
			return ACTIVE;
		} else if (element.hasChild("inactive",NAMESPACE)) {
			return INACTIVE;
		} else if (element.hasChild("composing",NAMESPACE)) {
			return COMPOSING;
		} else if (element.hasChild("gone",NAMESPACE)) {
			return GONE;
		} else if (element.hasChild("paused",NAMESPACE)) {
			return PAUSED;
		} else {
			return null;
		}
	}

	public static Element toElement(ChatState state) {
		final String NAMESPACE = "http://jabber.org/protocol/chatstates";
		final Element element = new Element(state.toString().toLowerCase());
		element.setAttribute("xmlns",NAMESPACE);
		return element;
	}
}
