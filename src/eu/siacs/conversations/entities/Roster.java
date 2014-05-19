package eu.siacs.conversations.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Roster {
	Account account;
	HashMap<String, Contact> contacts = new HashMap<String, Contact>();
	private String version = null;
	
	public Roster(Account account) {
		this.account = account;
	}
	
	public boolean hasContact(String jid) {
		String cleanJid = jid.split("/")[0];
		return contacts.containsKey(cleanJid);
	}
	
	public Contact getContact(String jid) {
		String cleanJid = jid.split("/")[0];
		if (contacts.containsKey(cleanJid)) {
			return contacts.get(cleanJid);
		} else {
			Contact contact = new Contact(cleanJid);
			contact.setAccount(account);
			contacts.put(cleanJid, contact);
			return contact;
		}
	}

	public void clearPresences() {
		// TODO Auto-generated method stub
		
	}
	
	public void markAllAsNotInRoster() {
		
	}

	public List<Contact> getContacts() {
		return new ArrayList<Contact>(this.contacts.values());
	}

	public void initContact(Contact contact) {
		contact.setAccount(account);
		contact.setOption(Contact.Options.IN_ROSTER);
		contacts.put(contact.getJid(),contact);
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
