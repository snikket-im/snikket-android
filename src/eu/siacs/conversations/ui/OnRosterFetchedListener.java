package eu.siacs.conversations.ui;

import java.util.List;

import eu.siacs.conversations.entities.Contact;

public interface OnRosterFetchedListener {
	public void onRosterFetched(List<Contact> roster);
}
