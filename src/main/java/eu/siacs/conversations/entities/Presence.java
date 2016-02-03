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
	protected ServiceDiscoveryResult disco;
	protected final String ver;
	protected final String hash;

	private Presence(Status status, String ver, String hash) {
		this.status = status;
		this.ver = ver;
		this.hash = hash;
	}

	public static Presence parse(Element show, Element caps) {
		final String hash = caps == null ? null : caps.getAttribute("hash");
		final String ver = caps == null ? null : caps.getAttribute("ver");
		if ((show == null) || (show.getContent() == null)) {
			return new Presence(Status.ONLINE, ver, hash);
		} else if (show.getContent().equals("away")) {
			return new Presence(Status.AWAY, ver, hash);
		} else if (show.getContent().equals("xa")) {
			return new Presence(Status.XA, ver, hash);
		} else if (show.getContent().equals("chat")) {
			return new Presence(Status.CHAT, ver, hash);
		} else if (show.getContent().equals("dnd")) {
			return new Presence(Status.DND, ver, hash);
		} else {
			return new Presence(Status.OFFLINE, ver, hash);
		}
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}

	public boolean hasCaps() {
		return ver != null && hash != null;
	}

	public String getVer() {
		return this.ver;
	}

	public String getHash() {
		return this.hash;
	}

	public void setServiceDiscoveryResult(ServiceDiscoveryResult disco) {
		this.disco = disco;
	}
}
