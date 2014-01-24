package de.gultsch.chat.entities;

public class Message {

	String msg;
	
	public Message(String msg) {
		this.msg = msg;
	}
	
	public String toString() {
		return msg;
	}

	public String getTimeReadable() {
		return "2 min";
	}

}
