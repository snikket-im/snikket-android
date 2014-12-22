package eu.siacs.conversations.persistance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.xmpp.jid.Jid;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseBackend extends SQLiteOpenHelper {

	private static DatabaseBackend instance = null;

	private static final String DATABASE_NAME = "history";
	private static final int DATABASE_VERSION = 12;

	private static String CREATE_CONTATCS_STATEMENT = "create table "
			+ Contact.TABLENAME + "(" + Contact.ACCOUNT + " TEXT, "
			+ Contact.SERVERNAME + " TEXT, " + Contact.SYSTEMNAME + " TEXT,"
			+ Contact.JID + " TEXT," + Contact.KEYS + " TEXT,"
			+ Contact.PHOTOURI + " TEXT," + Contact.OPTIONS + " NUMBER,"
			+ Contact.SYSTEMACCOUNT + " NUMBER, " + Contact.AVATAR + " TEXT, "
            + Contact.LAST_PRESENCE + " TEXT, " + Contact.LAST_TIME + " NUMBER, "
			+ Contact.GROUPS + " TEXT, FOREIGN KEY(" + Contact.ACCOUNT + ") REFERENCES "
			+ Account.TABLENAME + "(" + Account.UUID
			+ ") ON DELETE CASCADE, UNIQUE(" + Contact.ACCOUNT + ", "
			+ Contact.JID + ") ON CONFLICT REPLACE);";

	private DatabaseBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("PRAGMA foreign_keys=ON;");
		db.execSQL("create table " + Account.TABLENAME + "(" + Account.UUID
				+ " TEXT PRIMARY KEY," + Account.USERNAME + " TEXT,"
				+ Account.SERVER + " TEXT," + Account.PASSWORD + " TEXT,"
				+ Account.ROSTERVERSION + " TEXT," + Account.OPTIONS
				+ " NUMBER, " + Account.AVATAR + " TEXT, " + Account.KEYS
				+ " TEXT)");
		db.execSQL("create table " + Conversation.TABLENAME + " ("
				+ Conversation.UUID + " TEXT PRIMARY KEY, " + Conversation.NAME
				+ " TEXT, " + Conversation.CONTACT + " TEXT, "
				+ Conversation.ACCOUNT + " TEXT, " + Conversation.CONTACTJID
				+ " TEXT, " + Conversation.CREATED + " NUMBER, "
				+ Conversation.STATUS + " NUMBER, " + Conversation.MODE
				+ " NUMBER, " + Conversation.ATTRIBUTES + " TEXT, FOREIGN KEY("
				+ Conversation.ACCOUNT + ") REFERENCES " + Account.TABLENAME
				+ "(" + Account.UUID + ") ON DELETE CASCADE);");
		db.execSQL("create table " + Message.TABLENAME + "( " + Message.UUID
				+ " TEXT PRIMARY KEY, " + Message.CONVERSATION + " TEXT, "
				+ Message.TIME_SENT + " NUMBER, " + Message.COUNTERPART
				+ " TEXT, " + Message.TRUE_COUNTERPART + " TEXT,"
				+ Message.BODY + " TEXT, " + Message.ENCRYPTION + " NUMBER, "
				+ Message.STATUS + " NUMBER," + Message.TYPE + " NUMBER, "
				+ Message.RELATIVE_FILE_PATH + " TEXT, "
				+ Message.SERVER_MSG_ID + " TEXT, "
				+ Message.REMOTE_MSG_ID + " TEXT, FOREIGN KEY("
				+ Message.CONVERSATION + ") REFERENCES "
				+ Conversation.TABLENAME + "(" + Conversation.UUID
				+ ") ON DELETE CASCADE);");

		db.execSQL(CREATE_CONTATCS_STATEMENT);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion < 2 && newVersion >= 2) {
			db.execSQL("update " + Account.TABLENAME + " set "
					+ Account.OPTIONS + " = " + Account.OPTIONS + " | 8");
		}
		if (oldVersion < 3 && newVersion >= 3) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.TYPE + " NUMBER");
		}
		if (oldVersion < 5 && newVersion >= 5) {
			db.execSQL("DROP TABLE " + Contact.TABLENAME);
			db.execSQL(CREATE_CONTATCS_STATEMENT);
			db.execSQL("UPDATE " + Account.TABLENAME + " SET "
					+ Account.ROSTERVERSION + " = NULL");
		}
		if (oldVersion < 6 && newVersion >= 6) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.TRUE_COUNTERPART + " TEXT");
		}
		if (oldVersion < 7 && newVersion >= 7) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.REMOTE_MSG_ID + " TEXT");
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.AVATAR + " TEXT");
			db.execSQL("ALTER TABLE " + Account.TABLENAME + " ADD COLUMN "
					+ Account.AVATAR + " TEXT");
		}
		if (oldVersion < 8 && newVersion >= 8) {
			db.execSQL("ALTER TABLE " + Conversation.TABLENAME + " ADD COLUMN "
					+ Conversation.ATTRIBUTES + " TEXT");
		}
        if (oldVersion < 9 && newVersion >= 9) {
            db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
                    + Contact.LAST_TIME + " NUMBER");
            db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
                    + Contact.LAST_PRESENCE + " TEXT");
        }
		if (oldVersion < 10 && newVersion >= 10) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.RELATIVE_FILE_PATH + " TEXT");
		}
		if (oldVersion < 11 && newVersion >= 11) {
			db.execSQL("ALTER TABLE " + Contact.TABLENAME + " ADD COLUMN "
					+ Contact.GROUPS + " TEXT");
			db.execSQL("delete from "+Contact.TABLENAME);
			db.execSQL("update "+Account.TABLENAME+" set "+Account.ROSTERVERSION+" = NULL");
		}
		if (oldVersion < 12 && newVersion >= 12) {
			db.execSQL("ALTER TABLE " + Message.TABLENAME + " ADD COLUMN "
					+ Message.SERVER_MSG_ID + " TEXT");
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
		int count = cursor.getInt(0);
		cursor.close();
		return count;
	}

	public CopyOnWriteArrayList<Conversation> getConversations(int status) {
		CopyOnWriteArrayList<Conversation> list = new CopyOnWriteArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { Integer.toString(status) };
		Cursor cursor = db.rawQuery("select * from " + Conversation.TABLENAME
				+ " where " + Conversation.STATUS + " = ? order by "
				+ Conversation.CREATED + " desc", selectionArgs);
		while (cursor.moveToNext()) {
			list.add(Conversation.fromCursor(cursor));
		}
		cursor.close();
		return list;
	}

	public ArrayList<Message> getMessages(Conversation conversations, int limit) {
		return getMessages(conversations, limit, -1);
	}

	public ArrayList<Message> getMessages(Conversation conversation, int limit,
			long timestamp) {
		ArrayList<Message> list = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		if (timestamp == -1) {
			String[] selectionArgs = { conversation.getUuid() };
			cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
					+ "=?", selectionArgs, null, null, Message.TIME_SENT
					+ " DESC", String.valueOf(limit));
		} else {
			String[] selectionArgs = { conversation.getUuid(),
					Long.toString(timestamp) };
			cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
					+ "=? and " + Message.TIME_SENT + "<?", selectionArgs,
					null, null, Message.TIME_SENT + " DESC",
					String.valueOf(limit));
		}
		if (cursor.getCount() > 0) {
			cursor.moveToLast();
			do {
				Message message = Message.fromCursor(cursor);
				message.setConversation(conversation);
				list.add(message);
			} while (cursor.moveToPrevious());
		}
		cursor.close();
		return list;
	}

	public Conversation findConversation(final Account account, final Jid contactJid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { account.getUuid(), contactJid.toBareJid().toString() + "%" };
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.ACCOUNT + "=? AND " + Conversation.CONTACTJID
						+ " like ?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0)
			return null;
		cursor.moveToFirst();
		Conversation conversation = Conversation.fromCursor(cursor);
		cursor.close();
		return conversation;
	}

	public void updateConversation(final Conversation conversation) {
		final SQLiteDatabase db = this.getWritableDatabase();
		final String[] args = { conversation.getUuid() };
		db.update(Conversation.TABLENAME, conversation.getContentValues(),
				Conversation.UUID + "=?", args);
	}

	public List<Account> getAccounts() {
		List<Account> list = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(Account.TABLENAME, null, null, null, null,
				null, null);
		while (cursor.moveToNext()) {
			list.add(Account.fromCursor(cursor));
		}
		cursor.close();
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

	public boolean hasEnabledAccounts() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery("select count(" + Account.UUID + ")  from "
				+ Account.TABLENAME + " where not options & (1 <<1)", null);
		try {
			cursor.moveToFirst();
			int count = cursor.getInt(0);
			cursor.close();
			return (count > 0);
		} catch (SQLiteCantOpenDatabaseException e) {
			return true; // better safe than sorry
		}
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

	public void readRoster(Roster roster) {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		String args[] = { roster.getAccount().getUuid() };
		cursor = db.query(Contact.TABLENAME, null, Contact.ACCOUNT + "=?",
				args, null, null, null);
		while (cursor.moveToNext()) {
			roster.initContact(Contact.fromCursor(cursor));
		}
		cursor.close();
	}

	public void writeRoster(final Roster roster) {
		final Account account = roster.getAccount();
		final SQLiteDatabase db = this.getWritableDatabase();
		for (Contact contact : roster.getContacts()) {
			if (contact.getOption(Contact.Options.IN_ROSTER)) {
				db.insert(Contact.TABLENAME, null, contact.getContentValues());
			} else {
				String where = Contact.ACCOUNT + "=? AND " + Contact.JID + "=?";
				String[] whereArgs = { account.getUuid(), contact.getJid().toString() };
				db.delete(Contact.TABLENAME, where, whereArgs);
			}
		}
		account.setRosterVersion(roster.getVersion());
		updateAccount(account);
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

	public Conversation findConversationByUuid(String conversationUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { conversationUuid };
		Cursor cursor = db.query(Conversation.TABLENAME, null,
				Conversation.UUID + "=?", selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		Conversation conversation = Conversation.fromCursor(cursor);
		cursor.close();
		return conversation;
	}

	public Message findMessageByUuid(String messageUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { messageUuid };
		Cursor cursor = db.query(Message.TABLENAME, null, Message.UUID + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		Message message = Message.fromCursor(cursor);
		cursor.close();
		return message;
	}

	public Account findAccountByUuid(String accountUuid) {
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = { accountUuid };
		Cursor cursor = db.query(Account.TABLENAME, null, Account.UUID + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			return null;
		}
		cursor.moveToFirst();
		Account account = Account.fromCursor(cursor);
		cursor.close();
		return account;
	}

	public List<Message> getImageMessages(Conversation conversation) {
		ArrayList<Message> list = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
			String[] selectionArgs = { conversation.getUuid(), String.valueOf(Message.TYPE_IMAGE) };
			cursor = db.query(Message.TABLENAME, null, Message.CONVERSATION
					+ "=? AND "+Message.TYPE+"=?", selectionArgs, null, null,null);
		if (cursor.getCount() > 0) {
			cursor.moveToLast();
			do {
				Message message = Message.fromCursor(cursor);
				message.setConversation(conversation);
				list.add(message);
			} while (cursor.moveToPrevious());
		}
		cursor.close();
		return list;
	}
}
