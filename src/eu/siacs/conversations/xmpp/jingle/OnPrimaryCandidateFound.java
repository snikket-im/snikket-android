package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.xml.Element;

public interface OnPrimaryCandidateFound {
	public void onPrimaryCandidateFound(boolean success, Element canditate);
}
