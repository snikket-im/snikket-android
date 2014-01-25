package de.gultsch.chat.entities;

import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class Conversation extends AbstractEntity {

	private static final long serialVersionUID = -6727528868973996739L;
	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;

	public static final String NAME = "name";
	public static final String PHOTO_URI = "profilePhotoUri";
	public static final String ACCOUNT = "accountUuid";
	public static final String CONTACT = "contactJid";
	public static final String STATUS = "status";
	public static final String CREATED = "created";

	private String name;
	private String profilePhotoUri;
	private String accountUuid;
	private String contactJid;
	private int status;
	private long created;

	private transient List<Message> messages;

	public Conversation(String name, Uri profilePhoto, Account account,
			String contactJid) {
		this(java.util.UUID.randomUUID().toString(), name, profilePhoto
				.toString(), account.getUuid(), contactJid, System
				.currentTimeMillis(), STATUS_AVAILABLE);
	}

	public Conversation(String uuid, String name, String profilePhoto,
			String accountUuid, String contactJid, long created, int status) {
		this.uuid = uuid;
		this.name = name;
		this.profilePhotoUri = profilePhoto;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.created = created;
		this.status = status;
	}

	public List<Message> getMessages() {
		return messages;
	}

	public void setMessages(List<Message> msgs) {
		this.messages = msgs;
	}

	public String getName() {
		return this.name;
	}

	public String getProfilePhotoString() {
		return this.profilePhotoUri;
	}

	public String getAccountUuid() {
		return this.accountUuid;
	}

	public String getContactJid() {
		return this.contactJid;
	}

	public Uri getProfilePhotoUri() {
		if (this.profilePhotoUri != null) {
			return Uri.parse(profilePhotoUri);
		}
		return null;
	}

	public int getStatus() {
		return this.status;
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(NAME, name);
		values.put(PHOTO_URI, profilePhotoUri);
		values.put(ACCOUNT, accountUuid);
		values.put(CONTACT, contactJid);
		values.put(CREATED, created);
		values.put(STATUS, status);
		return values;
	}

	public static Conversation fromCursor(Cursor cursor) {
		return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(NAME)),
				cursor.getString(cursor.getColumnIndex(PHOTO_URI)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(CONTACT)),
				cursor.getLong(cursor.getColumnIndex(CREATED)),
				cursor.getInt(cursor.getColumnIndex(STATUS)));
	}
}
