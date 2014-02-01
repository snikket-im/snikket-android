package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;

public class IqPacket extends Element {
	
	public static final int TYPE_SET = 0;
	public static final int TYPE_RESULT = 1;
	public static final int TYPE_GET = 2;

	private IqPacket(String name) {
		super(name);
	}

	public IqPacket(int type) {
		super("iq");
		switch (type) {
		case TYPE_SET:
			this.setAttribute("type", "set");
			break;
		case TYPE_GET:
			this.setAttribute("type", "get");
			break;
		case TYPE_RESULT:
			this.setAttribute("type", "result");
			break;
		default:
			break;
		}
	}
	
	public IqPacket() {
		super("iq");
	}

	public String getId() {
		return this.getAttribute("id");
	}

}
