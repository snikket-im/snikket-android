package eu.siacs.conversations.xmpp.jingle;

public interface OnPrimaryCandidateFound {
	void onPrimaryCandidateFound(boolean success, JingleCandidate canditate);
}
