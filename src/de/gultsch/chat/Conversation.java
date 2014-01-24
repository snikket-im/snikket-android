package de.gultsch.chat;

import java.util.ArrayList;


public class Conversation {
	private String name;
	private ArrayList<Message> msgs = new ArrayList<Message>();
	
	public Conversation(String name) {
		this.name = name;
	}
	
	public ArrayList<Message> getLastMessages(int count, int offset) {		
		msgs.add(new Message("this is my last message"));
		return msgs;
	}
	public String getName() {
		return this.name;
	}
}
