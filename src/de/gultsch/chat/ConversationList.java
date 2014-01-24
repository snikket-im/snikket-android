package de.gultsch.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.gultsch.chat.entities.Conversation;

public class ConversationList extends ArrayList<Conversation> {
	
	private static final long serialVersionUID = 3661496589984289968L;
	
	private int selectedConversationPosition = -1;
	
	private ConversationCursor cursor = new ConversationCursor(this);

	public ConversationCursor getCursor() {
		return this.cursor;
	}

	public Conversation getSelectedConversation() {
		return this.get(this.selectedConversationPosition);
	}

	public void setSelectedConversationPosition(int selectedConversation) {
		this.selectedConversationPosition = selectedConversation;
	}
	
	public synchronized int addAndReturnPosition(Conversation conversation) {
		this.add(conversation);
		return size() - 1;
	}
}
