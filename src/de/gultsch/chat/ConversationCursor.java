package de.gultsch.chat;


import java.util.ArrayList;

import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;


import android.database.AbstractCursor;

public class ConversationCursor extends AbstractCursor {

	
	protected ConversationList conversations;
	
	public static final String NAME = "conversationname";
	public static final String LAST_MSG = "lastmsg";
	public static final String DATE = "date";
	public static final String ID = "_id";
	
	public ConversationCursor(ConversationList list) {
		super();
		this.conversations = list;
	}
	
	public ArrayList<Conversation> getConversationOverview() {
		return this.conversations;
	}
	
	public void setConversationOverview(ConversationList list) {
		this.conversations = list;
	}
	
	@Override
	public String[] getColumnNames() {
		return new String[]{ID,NAME,LAST_MSG,DATE};
	}

	@Override
	public int getCount() {
		return conversations.size();
	}

	@Override
	public double getDouble(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getFloat(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getInt(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getLong(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public short getShort(int column) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getString(int column) {
		Conversation conversation = conversations.get(getPosition());
		Message lastMessage = conversation.getLastMessages(1,0).get(0);
		switch (column) {
		case 1:
			return conversation.getName();
		case 2:
			return lastMessage.toString();
		case 3:
			return lastMessage.getTimeReadable();
		default:
			return null;
		}
	}

	@Override
	public boolean isNull(int column) {
		// TODO Auto-generated method stub
		return false;
	}
	
}