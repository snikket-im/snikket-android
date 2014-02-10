package de.gultsch.chat.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

public class Account  extends AbstractEntity{

	private static final long serialVersionUID = 6174825093869578035L;
	
	public static final String TABLENAME = "accounts";
	
	public static final String USERNAME = "username";
	public static final String SERVER = "server";
	public static final String PASSWORD = "password";
	public static final String OPTIONS = "options";
	public static final String ROSTERVERSION = "rosterversion";
	
	public static final int OPTION_USETLS = 0;
	public static final int OPTION_DISABLED = 1;
	
	public static final int STATUS_DISABLED = -1;
	public static final int STATUS_OFFLINE = 0;
	public static final int STATUS_ONLINE = 1;
	public static final int STATUS_UNAUTHORIZED = 2;
	public static final int STATUS_NOINTERNET = 3;
	public static final int STATUS_TLS_ERROR = 4;
	public static final int STATUS_SERVER_NOT_FOUND = 5;
	
	protected String username;
	protected String server;
	protected String password;
	protected int options = 0;
	protected String rosterVersion;
	protected String resource;
	protected int status = 0;
	
	protected boolean online = false;
	
	public Account() {
		this.uuid = "0";
	}
	
	public Account(String username, String server, String password) {
		this(java.util.UUID.randomUUID().toString(),username,server,password,0,null);
	}
	public Account(String uuid, String username, String server,String password, int options, String rosterVersion) {
		this.uuid = uuid;
		this.username = username;
		this.server = server;
		this.password = password;
		this.options = options;
		this.rosterVersion = rosterVersion;
	}
	
	public boolean isOptionSet(int option) {
		return ((options & (1 << option)) != 0);
	}
	
	public void setOption(int option, boolean value) {
		if (value) {
			this.options = (this.options | 1 << option);
		} else {
			this.options = (this.options & 0 << option);
		}
	}
	
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
	public int getStatus() {
		if (isOptionSet(OPTION_DISABLED)) {
			return STATUS_DISABLED;
		} else {
			return this.status;
		}
	}
	
	public void setResource(String resource) {
		this.resource = resource;
	}
	
	public String getJid() {
		return username+"@"+server;
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID,uuid);
		values.put(USERNAME, username);
		values.put(SERVER, server);
		values.put(PASSWORD, password);
		values.put(OPTIONS,options);
		return values;
	}
	
	public static Account fromCursor(Cursor cursor) {
		return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(USERNAME)),
				cursor.getString(cursor.getColumnIndex(SERVER)),
				cursor.getString(cursor.getColumnIndex(PASSWORD)),
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
				cursor.getString(cursor.getColumnIndex(ROSTERVERSION))
				);
	}

}
