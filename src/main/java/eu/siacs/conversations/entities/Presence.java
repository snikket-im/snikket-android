package eu.siacs.conversations.entities;

import java.lang.Comparable;

import eu.siacs.conversations.xml.Element;

public class Presence implements Comparable {

	public enum Status {
		CHAT, ONLINE, AWAY, XA, DND, OFFLINE;

		public String toShowString() {
			switch(this) {
				case CHAT: return "chat";
				case AWAY: return "away";
				case XA:   return "xa";
				case DND:  return "dnd";
			}

			return null;
		}
	}

	protected final Status status;
	protected final ServiceDiscoveryResult disco;

	public Presence(Element show, ServiceDiscoveryResult disco) {
		this.disco = disco;

		if ((show == null) || (show.getContent() == null)) {
			this.status = Status.ONLINE;
		} else if (show.getContent().equals("away")) {
			this.status = Status.AWAY;
		} else if (show.getContent().equals("xa")) {
			this.status = Status.XA;
		} else if (show.getContent().equals("chat")) {
			this.status = Status.CHAT;
		} else if (show.getContent().equals("dnd")) {
			this.status = Status.DND;
		} else {
			this.status = Status.OFFLINE;
		}
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}
}
