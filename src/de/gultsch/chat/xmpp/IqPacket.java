package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;

public class IqPacket extends Element {
	
	public static final int TYPE_SET = 0;
	public static final int TYPE_RESULT = 1;

	private IqPacket(String name) {
		super(name);
	}

	public IqPacket(String id, int type) {
		super("iq");
		this.setAttribute("id",id);
		switch (type) {
		case TYPE_SET:
			this.setAttribute("type", "set");
			break;
		default:
			break;
		}
	}

}
