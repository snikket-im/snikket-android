package de.gultsch.chat.persistance;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.entities.Conversation;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseBackend extends SQLiteOpenHelper {
	
	private static DatabaseBackend instance = null;
	
	private static final String DATABASE_NAME = "history";
	private static final int DATABASE_VERSION = 1;

	public DatabaseBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("create table conversations (uuid TEXT, name TEXT, profilePhotoUri TEXT, accountUuid TEXT, contactJid TEXT, created NUMBER, status NUMBER)");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {
		// TODO Auto-generated method stub

	}
	
	public static synchronized DatabaseBackend getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseBackend(context);
		}
		return instance;
	}
	
	public void addConversation(Conversation conversation) {
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert("conversations", null, conversation.getContentValues());
	}
	
	
	public int getConversationCount() {
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery("select count(uuid) as count from conversations",null);
		cursor.moveToFirst();
		return cursor.getInt(0);
	}

	public List<Conversation> getConversations(int status) {
		List<Conversation> list = new ArrayList<Conversation>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {""+status};
		Cursor cursor = db.rawQuery("select * from conversations where status = ? order by created desc", selectionArgs);
		while(cursor.moveToNext()) {
			list.add(Conversation.fromCursor(cursor));
		}
		return list;
	}

}
