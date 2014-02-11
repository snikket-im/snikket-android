package de.gultsch.chat.entities;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class Conversation extends AbstractEntity {

	private static final long serialVersionUID = -6727528868973996739L;
	
	public static final String TABLENAME = "conversations";
	
	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;
	
	public static final int MODE_MULTI = 1;
	public static final int MODE_SINGLE = 0;

	public static final String NAME = "name";
	public static final String ACCOUNT = "accountUuid";
	public static final String CONTACT = "contactUuid";
	public static final String CONTACTJID = "contactJid";
	public static final String STATUS = "status";
	public static final String CREATED = "created";
	public static final String MODE = "mode";

	private String name;
	private String contactUuid;
	private String accountUuid;
	private String contactJid;
	private int status;
	private long created;
	private int mode;

	private transient List<Message> messages = null;
	private transient Account account = null;
	private transient Contact contact;

	public Conversation(String name, Account account,
			String contactJid, int mode) {
		this(java.util.UUID.randomUUID().toString(), name, null, account.getUuid(), contactJid, System
				.currentTimeMillis(), STATUS_AVAILABLE,mode);
		this.account = account;
	}

	public Conversation(String uuid, String name, String contactUuid,
			String accountUuid, String contactJid, long created, int status, int mode) {
		this.uuid = uuid;
		this.name = name;
		this.contactUuid = contactUuid;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.created = created;
		this.status = status;
		this.mode = mode;
	}

	public List<Message> getMessages() {
		if (messages == null) this.messages = new ArrayList<Message>(); //prevent null pointer
		
		//populate with Conversation (this)
		
		for(Message msg : messages) {
			msg.setConversation(this);
		}
		
		return messages;
	}
	
	public boolean isRead() {
		if ((this.messages == null)||(this.messages.size() == 0)) return true;
		return this.messages.get(this.messages.size() - 1).isRead();
	}
	
	public void markRead() {
		Log.d("xmppService", "inside mark read method");
		if (this.messages == null) return;
		for(int i = this.messages.size() -1; i >= 0; --i) {
			if (messages.get(i).isRead()) return;
			this.messages.get(i).markRead();
		}
	}
	
	public String getLatestMessage() {
		if ((this.messages == null)||(this.messages.size()==0)) {
			return null;
		} else {
			return this.messages.get(this.messages.size() - 1).getBody();
		}
	}
	
	public long getLatestMessageDate() {
		if ((this.messages == null)||(this.messages.size()==0)) {
			return this.getCreated();
		} else {
			return this.messages.get(this.messages.size() - 1).getTimeSent();
		}
	}

	public void setMessages(List<Message> msgs) {
		this.messages = msgs;
	}

	public String getName() {
		if (this.contact!=null) {
			return this.contact.getDisplayName();
		} else {
			return this.name;
		}
	}

	public String getProfilePhotoString() {
		if (this.contact==null) {
			return null;
		} else {
			return this.contact.getProfilePhoto();
		}
	}

	public String getAccountUuid() {
		return this.accountUuid;
	}
	
	public Account getAccount() {
		return this.account;
	}
	
	public Contact getContact() {
		return this.contact;
	}
	
	public void setContact(Contact contact) {
		this.contact = contact;
		if (contact!=null) {
			this.contactUuid = contact.getUuid();
		}
	}

	public void setAccount(Account account) {
		this.account = account;
	}
	
	public String getContactJid() {
		return this.contactJid;
	}

	public Uri getProfilePhotoUri() {
		if (this.getProfilePhotoString() != null) {
			return Uri.parse(this.getProfilePhotoString());
		}
		return null;
	}

	public int getStatus() {
		return this.status;
	}
	
	public long getCreated() {
		return this.created;
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(NAME, name);
		values.put(CONTACT, contactUuid);
		values.put(ACCOUNT, accountUuid);
		values.put(CONTACTJID, contactJid);
		values.put(CREATED, created);
		values.put(STATUS, status);
		values.put(MODE,mode);
		return values;
	}

	public static Conversation fromCursor(Cursor cursor) {
		return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(NAME)),
				cursor.getString(cursor.getColumnIndex(CONTACT)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(CONTACTJID)),
				cursor.getLong(cursor.getColumnIndex(CREATED)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(MODE)));
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getMode() {
		return this.mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}
}
