package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Contact;

public interface OnContactStatusChanged {
	public void onContactStatusChanged(Contact contact, boolean online);
}
