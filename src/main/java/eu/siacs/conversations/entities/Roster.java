package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.xmpp.jid.Jid;

public class Roster {
	final Account account;
	final ConcurrentHashMap<String, Contact> contacts = new ConcurrentHashMap<>();
	private String version = null;

	public Roster(Account account) {
		this.account = account;
	}

	public Contact getContactFromRoster(Jid jid) {
		if (jid == null) {
			return null;
		}
		final Contact contact = contacts.get(jid.toBareJid().toString());
		if (contact != null && contact.showInRoster()) {
			return contact;
		} else {
			return null;
		}
	}

	public Contact getContact(final Jid jid) {
		final Jid bareJid = jid.toBareJid();
		if (contacts.containsKey(bareJid.toString())) {
			return contacts.get(bareJid.toString());
		} else {
			final Contact contact = new Contact(bareJid);
			contact.setAccount(account);
			contacts.put(bareJid.toString(), contact);
			return contact;
		}
	}

	public void clearPresences() {
		for (Contact contact : getContacts()) {
			contact.clearPresences();
		}
	}

	public void markAllAsNotInRoster() {
		for (final Contact contact : getContacts()) {
			contact.resetOption(Contact.Options.IN_ROSTER);
		}
	}

	public void clearSystemAccounts() {
		for (final Contact contact : getContacts()) {
			contact.setPhotoUri(null);
			contact.setSystemName(null);
			contact.setSystemAccount(null);
		}
	}

	public List<Contact> getContacts() {
		return new ArrayList<>(this.contacts.values());
	}

	public void initContact(final Contact contact) {
		contact.setAccount(account);
		contact.setOption(Contact.Options.IN_ROSTER);
		contacts.put(contact.getJid().toBareJid().toString(), contact);
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getVersion() {
		return this.version;
	}

	public Account getAccount() {
		return this.account;
	}
}
