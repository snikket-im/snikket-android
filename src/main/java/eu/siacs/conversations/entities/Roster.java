package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.android.AbstractPhoneContact;
import rocks.xmpp.addr.Jid;


public class Roster {
	final Account account;
	final HashMap<Jid, Contact> contacts = new HashMap<>();
	private String version = null;

	public Roster(Account account) {
		this.account = account;
	}

	public Contact getContactFromRoster(Jid jid) {
		if (jid == null) {
			return null;
		}
		synchronized (this.contacts) {
			Contact contact = contacts.get(jid.asBareJid());
			if (contact != null && contact.showInRoster()) {
				return contact;
			} else {
				return null;
			}
		}
	}

	public Contact getContact(final Jid jid) {
		synchronized (this.contacts) {
			if (!contacts.containsKey(jid.asBareJid())) {
				Contact contact = new Contact(jid.asBareJid());
				contact.setAccount(account);
				contacts.put(contact.getJid().asBareJid(), contact);
				return contact;
			}
			return contacts.get(jid.asBareJid());
		}
	}

	public void clearPresences() {
		for (Contact contact : getContacts()) {
			contact.clearPresences();
		}
	}

	public void markAllAsNotInRoster() {
		for (Contact contact : getContacts()) {
			contact.resetOption(Contact.Options.IN_ROSTER);
		}
	}

	public List<Contact> getWithSystemAccounts(Class<?extends AbstractPhoneContact> clazz) {
		int option = Contact.getOption(clazz);
		List<Contact> with = getContacts();
		for(Iterator<Contact> iterator = with.iterator(); iterator.hasNext();) {
			Contact contact = iterator.next();
			if (!contact.getOption(option)) {
				iterator.remove();
			}
		}
		return with;
	}

	public List<Contact> getContacts() {
		synchronized (this.contacts) {
			return new ArrayList<>(this.contacts.values());
		}
	}

	public void initContact(final Contact contact) {
		if (contact == null) {
			return;
		}
		contact.setAccount(account);
		synchronized (this.contacts) {
			contacts.put(contact.getJid().asBareJid(), contact);
		}
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
