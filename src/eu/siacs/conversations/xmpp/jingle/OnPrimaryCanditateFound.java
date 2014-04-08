package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.xml.Element;

public interface OnPrimaryCanditateFound {
	public void onPrimaryCanditateFound(boolean success, Element canditate);
}
