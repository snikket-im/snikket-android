package de.gultsch.chat.entities;

import java.io.Serializable;

import android.net.Uri;

public class Contact implements Serializable {
	private static final long serialVersionUID = -4570817093119419962L;
	protected String display_name;
	protected String jid;
	protected String photo;
	
	public Contact(String display_name, String jid, String photo) {
		this.display_name = display_name;
		this.jid = jid;
		this.photo = photo;
	}

	public String getDisplayName() {
		return this.display_name;
	}

	public String getProfilePhoto() {
		return photo;
	}

	public String getJid() {
		return this.jid;
	}
	
	public boolean match(String needle) {
		return (jid.toLowerCase().contains(needle.toLowerCase()) || (display_name.toLowerCase().contains(needle.toLowerCase())));
	}
}
