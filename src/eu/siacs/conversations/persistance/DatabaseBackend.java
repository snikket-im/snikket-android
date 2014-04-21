package eu.siacs.conversations.persistance;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presences;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.util.Log;

public class DatabaseBackend extends SQLiteOpenHelper {

	private static DatabaseBackend instance = null;

	private static final String DATABASE_NAME = "history";
	private static final int DATABASE_VERSION = 3;

	public DatabaseBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("PRAGMA foreign_keys=ON;");
		db.execSQL("create table " + Account.TABLENAME + "(" + Account.UUID
				+ " TEXT PRIMARY KEY," + Account.USERNAME + " TEXT,"
				+ Account.SERVER + " TEXT," + Account.PASSWORD + " TEXT,"
				+ Account.ROSTERVERSION + " TEXT," + Account.OPTIONS
				+ " NUMBER, "+Account.KEYS+" TEXT)");
		db.execSQL("create table " + Conversation.TABLENAME + " ("
				+ Conversation.UUID + " TEXT PRIMARY KEY, " + Conversation.NAME
				+ " TEXT, " + Conversation.CONTACT + " TEXT, "
				+ Conversation.ACCOUNT + " TEXT, " + Conversation.CONTACTJID
				+ " TEXT, " + Conversation.CREATED + " NUMBER, "
				+ Conversation.STATUS + " NUMBER," + Conversation.MODE
				+ " NUMBER," + "FOREIGN KEY(" + Conversation.ACCOUNT
				+ ") REFERENCES " + Account.TABLENAME + "(" + Account.UUID
				+ ") ON DELETE CASCADE);");
		db.execSQL("create table " + Message.TABLENAME + "( " + Message.UUID
				+ " TEXT PRIMARY KEY, " + Message.CONVERSATION + " TEXT, "
				+ Message.TIME_SENT + " NUMBER, " + Message.COUNTERPART
				+ " TEXT, " + Message.BODY + " TEXT, " + Message.ENCRYPTION
				+ " NUMBER, " + Message.STATUS + " NUMBER," +Message.TYPE +" NUMBER, FOREIGN KEY("
				+ Message.CONVERSATION + ") REFERENCES "
				+ Conversation.TABLENAME + "(" + Conversation.UUID
				+ ") ON DELETE CASCADE);");
		db.execSQL("create table " + Contact.TABLENAME + "(" + Contact.UUID
				+ " TEXT PRIMARY KEY, " + Contact.ACCOUNT + " TEXT, "
				+ Contact.DISPLAYNAME + " TEXT," + Contact.JID + " TEXT,"
				+ Contact.PRESENCES + " TEXT, " + Contact.KEYS
				+ " TEXT," + Contact.PHOTOURI + " TEXT," + Contact.SUBSCRIPTION
				+ " NUMBER," + Contact.SYSTEMACCOUNT + " NUMBER, "
				+ "FOREIGN KEY(" + Contact.ACCOUNT + ") REFERENCES "
				+ Account.TABLENAME + "(" + Account.UUID
				+ ") ON DELETE CASCADE);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2 && newVersion >= 2) {
			// enable compression by default.
			db.execSQL("update " + Account.TABLENAME
				+ " set " + Account.OPTIONS + " = " + Account.OPTIONS + " | 8");
		}
		if (oldVersion < 3 && newVersion >= 3) {
			//add field type to message
			db.execSQL("ALTER TABLE "+Message.TABLENAME+" ADD COLUMN "+Message.TYPE+" NUMBER");;
		}
	}

	public static synchronized DatabaseBackend getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseBackend(context);
		}
		return instance;
	}

	public void createConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Conversation.TABLENAME, null, conversation.getContentValues());
	}

	public void createMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Message.TABLENAME, null, message.getContentValues());
	}

	public void createAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Account.TABLENAME, null, account.getContentValues());
	}
	
	public void createContact(Contact contact) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(Contact.TABLENAME, null, contact.getContentValues());
	}

	public int getConversationCount() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery("select count(uuid) as count from "
				+ Conversation.TABLENAME + " where " + Conversation.STATUS
				+ "=" + Conversation.STATUS_AVAILABLE, null);
		cursor.moveToFirst();
		return cursor.getInt(0);
	}

	public List<Conversation> getConversations(int status) {
		List<Conversation> list = new ArrayList<Conversation>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { "" + status };
		Cursor cursor = db.rawQuery("select * from " + Conversation.TABLENAME
				+ " where " + Conversation.STATUS + " = ? order by "
				+ Conversation.CREATED + " desc", selectionArgs);
		while (cursor.moveToNext()) {
			list.add(Conversation.fromCursor(cursor));
		}
		return list;
	}

	public List<Message> getMessages(Conversation conversation, int limit) {
		List<Message> list = new CopyOnWriteArrayList<Message>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { conversation.getUuid() };
		Cursor cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
				+ "=?", selectionArgs, null, null, Message.TIME_SENT + " DESC",
				String.valueOf(limit));
		if (cursor.getCount() > 0) {
			cursor.moveToLast();
			do {
				list.add(Message.fromCursor(cursor));
			} while (cursor.moveToPrevious());
		}
		return list;
	}

	public Conversation findConversation(Account account, String contactJid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { account.getUuid(), contactJid+"%" };
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.ACCOUNT + "=? AND " + Conversation.CONTACTJID + " like ?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0)
			return null;
		cursor.moveToFirst();
		return Conversation.fromCursor(cursor);
	}

	public void updateConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { conversation.getUuid() };
		db.update(Conversation.TABLENAME, conversation.getContentValues(),
				Conversation.UUID + "=?", args);
	}

	public List<Account> getAccounts() {
		List<Account> list = new ArrayList<Account>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(Account.TABLENAME, null, null, null, null,
				null, null);
		Log.d("gultsch", "found " + cursor.getCount() + " accounts");
		while (cursor.moveToNext()) {
			list.add(Account.fromCursor(cursor));
		}
		return list;
	}

	public void updateAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { account.getUuid() };
		db.update(Account.TABLENAME, account.getContentValues(), Account.UUID
				+ "=?", args);
	}

	public void deleteAccount(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { account.getUuid() };
		db.delete(Account.TABLENAME, Account.UUID + "=?", args);
	}

	@Override
	public SQLiteDatabase getWritableDatabase() {
		SQLiteDatabase db = super.getWritableDatabase();
		db.execSQL("PRAGMA foreign_keys=ON;");
		return db;
	}

	public void updateMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { message.getUuid() };
		db.update(Message.TABLENAME, message.getContentValues(), Message.UUID
				+ "=?", args);
	}
	
	public void updateContact(Contact contact, boolean updatePresences) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { contact.getUuid() };
		ContentValues values = contact.getContentValues();
		if (!updatePresences) {
			values.remove(Contact.PRESENCES);
		} else {
			values.remove(Contact.DISPLAYNAME);
			values.remove(Contact.PHOTOURI);
			values.remove(Contact.SYSTEMACCOUNT);
		}
		db.update(Contact.TABLENAME, contact.getContentValues(), Contact.UUID
				+ "=?", args);
	}
	
	public void clearPresences(Account account) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { account.getUuid() };
		ContentValues values = new ContentValues();
		values.put(Contact.PRESENCES,"[]");
		db.update(Contact.TABLENAME, values, Contact.ACCOUNT
				+ "=?", args);
	}
	
	public void mergeContacts(List<Contact> contacts) {
		SQLiteDatabase db = this.getWritableDatabase();
		for (int i = 0; i < contacts.size(); i++) {
			Contact contact = contacts.get(i);
			String[] columns = {Contact.UUID, Contact.PRESENCES};
			String[] args = {contact.getAccount().getUuid(), contact.getJid()};
			Cursor cursor = db.query(Contact.TABLENAME, columns,Contact.ACCOUNT+"=? AND "+Contact.JID+"=?", args, null, null, null);
			if (cursor.getCount()>=1) {
				cursor.moveToFirst();
				contact.setUuid(cursor.getString(0));
				updateContact(contact,false);
			} else {
				contact.setUuid(UUID.randomUUID().toString());
				createContact(contact);
			}
		}
	}

	public List<Contact> getContactsByAccount(Account account) {
		List<Contact> list = new ArrayList<Contact>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		if (account==null) {
			cursor = db.query(Contact.TABLENAME, null, null, null, null,
					null, null);
		} else {
			String args[] = {account.getUuid()};
			cursor = db.query(Contact.TABLENAME, null, Contact.ACCOUNT+"=?", args, null,
					null, null);
		}
		while (cursor.moveToNext()) {
			list.add(Contact.fromCursor(cursor));
		}
		return list;
	}
	
	public List<Contact> getContacts(String where) {
		List<Contact> list = new ArrayList<Contact>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(Contact.TABLENAME, null, where, null, null, null, null);
		while (cursor.moveToNext()) {
			list.add(Contact.fromCursor(cursor));
		}
		return list;
	}

	public Contact findContact(Account account, String jid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { account.getUuid(), jid };
		Cursor cursor = db.query(Contact.TABLENAME, null,
				Contact.ACCOUNT + "=? AND " + Contact.JID + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0)
			return null;
		cursor.moveToFirst();
		return Contact.fromCursor(cursor);
	}

	public void deleteMessage(Message message) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { message.getUuid() };
		db.delete(Message.TABLENAME, Message.UUID + "=?", args);
	}
	
	public void deleteMessagesInConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { conversation.getUuid() };
		db.delete(Message.TABLENAME, Message.CONVERSATION + "=?", args);
	}

	public void deleteContact(Contact contact) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { contact.getUuid() };
		db.delete(Contact.TABLENAME, Contact.UUID + "=?", args);
	}

	public Contact getContact(String uuid) {
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = { uuid };
		Cursor cursor = db.query(Contact.TABLENAME, null, Contact.UUID + "=?", args, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Contact.fromCursor(cursor);
	}

	public Conversation findConversationByUuid(String conversationUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { conversationUuid };
		Cursor cursor = db.query(Conversation.TABLENAME, null, Conversation.UUID + "=?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Conversation.fromCursor(cursor);
	}

	public Message findMessageByUuid(String messageUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { messageUuid };
		Cursor cursor = db.query(Message.TABLENAME, null, Message.UUID + "=?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Message.fromCursor(cursor);
	}
	
	public Account findAccountByUuid(String accountUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { accountUuid };
		Cursor cursor = db.query(Account.TABLENAME, null, Account.UUID + "=?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		return Account.fromCursor(cursor);
	}
}
