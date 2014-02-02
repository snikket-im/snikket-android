package de.gultsch.chat.ui;

import java.util.List;
import de.gultsch.chat.entities.Contact;

public interface OnRosterFetchedListener {
	public void onRosterFetched(List<Contact> roster);
}
