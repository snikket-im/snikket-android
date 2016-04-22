package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;


public class PresenceTemplate extends AbstractEntity {

	public static final String TABELNAME = "presence_templates";
	public static final String LAST_USED = "last_used";
	public static final String MESSAGE = "message";
	public static final String STATUS = "status";

	private long lastUsed = 0;
	private String statusMessage;
	private Presence.Status status = Presence.Status.ONLINE;

	public PresenceTemplate(Presence.Status status, String statusMessage) {
		this.status = status;
		this.statusMessage = statusMessage;
		this.lastUsed = System.currentTimeMillis();
		this.uuid = java.util.UUID.randomUUID().toString();
	}

	private PresenceTemplate() {

	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(LAST_USED, lastUsed);
		values.put(MESSAGE, statusMessage);
		values.put(STATUS, status.toShowString());
		values.put(UUID, uuid);
		return values;
	}

	public static PresenceTemplate fromCursor(Cursor cursor) {
		PresenceTemplate template = new PresenceTemplate();
		template.uuid = cursor.getString(cursor.getColumnIndex(UUID));
		template.lastUsed = cursor.getLong(cursor.getColumnIndex(LAST_USED));
		template.statusMessage = cursor.getString(cursor.getColumnIndex(MESSAGE));
		template.status = Presence.Status.fromShowString(cursor.getString(cursor.getColumnIndex(STATUS)));
		return template;
	}

	public Presence.Status getStatus() {
		return status;
	}

	public String getStatusMessage() {
		return statusMessage;
	}
}
