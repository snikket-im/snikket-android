package de.gultsch.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

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
	
	public void sort() {
		Conversation selectedConversation = this.get(selectedConversationPosition);
		//sort this
		Collections.sort(this, new Comparator<Conversation>() {

			@Override
			public int compare(Conversation lhs, Conversation rhs) {
				// TODO Auto-generated method stub
				return 0;
			}
		});
		
		this.selectedConversationPosition = this.indexOf(selectedConversation);
	}
}
