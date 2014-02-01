package de.gultsch.chat.entities;

import java.io.Serializable;

import android.content.ContentValues;

public abstract class AbstractEntity implements Serializable {

	private static final long serialVersionUID = -1895605706690653719L;
	
	public static final String UUID = "uuid";
	
	protected String uuid;
	
	public String getUuid() {
		return this.uuid;
	}
	
	public abstract ContentValues getContentValues();
	
	public boolean equals(AbstractEntity entity) {
		return this.getUuid().equals(entity.getUuid());
	}
	
}
