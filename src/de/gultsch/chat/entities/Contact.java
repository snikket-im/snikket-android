package de.gultsch.chat.entities;

import java.io.Serializable;

import android.content.ContentValues;
import android.database.Cursor;

public class Contact extends AbstractEntity implements Serializable {
	private static final long serialVersionUID = -4570817093119419962L;
	
	
	public static final String TABLENAME = "contacts";
	
	public static final String DISPLAYNAME = "name";
	public static final String JID = "jid";
	public static final String SUBSCRIPTION = "subscription";
	public static final String SYSTEMACCOUNT = "systemaccount";
	public static final String PHOTOURI = "photouri";
	public static final String OPENPGPKEY = "pgpkey";
	public static final String LASTONLINEPRESENCE = "presence";
	public static final String ACCOUNT = "accountUuid";
	
	protected String accountUuid;
	protected String displayName;
	protected String jid;
	protected String subscription;
	protected int systemAccount;
	protected String photoUri;
	protected String openPGPKey;
	protected long lastOnlinePresence;
	
	public Contact(Account account, String displayName, String jid, String photoUri) {
		if (account == null) {
			this.accountUuid = null;
		} else {
			this.accountUuid = account.getUuid();
		}
		this.displayName = displayName;
		this.jid = jid;
		this.photoUri = photoUri;
	}
	
	public Contact(String uuid, String account, String displayName, String jid, String subscription, String photoUri, int systemAccount, String pgpKey, long lastseen) {
		this.uuid = uuid;
		this.accountUuid = account;
		this.displayName = displayName;
		this.jid = jid;
		this.subscription = subscription;
		this.photoUri = photoUri;
		this.systemAccount = systemAccount;
		this.openPGPKey = pgpKey;
		this.lastOnlinePresence = lastseen;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getProfilePhoto() {
		return this.photoUri;
	}
	
	public String getJid() {
		return this.jid;
	}
	
	public boolean match(String needle) {
		return (jid.toLowerCase().contains(needle.toLowerCase()) || (displayName.toLowerCase().contains(needle.toLowerCase())));
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID,uuid);
		values.put(ACCOUNT,accountUuid);
		values.put(DISPLAYNAME, displayName);
		values.put(JID, jid);
		values.put(SUBSCRIPTION,subscription);
		values.put(SYSTEMACCOUNT, systemAccount);
		values.put(PHOTOURI,photoUri);
		values.put(OPENPGPKEY,openPGPKey);
		values.put(LASTONLINEPRESENCE,lastOnlinePresence);
		return values;
	}
	
	public static Contact fromCursor(Cursor cursor) {
		return new Contact(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				cursor.getString(cursor.getColumnIndex(DISPLAYNAME)),
				cursor.getString(cursor.getColumnIndex(JID)),
				cursor.getString(cursor.getColumnIndex(SUBSCRIPTION)),
				cursor.getString(cursor.getColumnIndex(PHOTOURI)),
				cursor.getInt(cursor.getColumnIndex(SYSTEMACCOUNT)),
				cursor.getString(cursor.getColumnIndex(OPENPGPKEY)),
				cursor.getLong(cursor.getColumnIndex(LASTONLINEPRESENCE))
				);
	}
}
