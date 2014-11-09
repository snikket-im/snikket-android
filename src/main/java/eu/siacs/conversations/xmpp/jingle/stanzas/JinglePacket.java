package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JinglePacket extends IqPacket {
	Content content = null;
	Reason reason = null;
	Element jingle = new Element("jingle");

	@Override
	public Element addChild(Element child) {
		if ("jingle".equals(child.getName())) {
			Element contentElement = child.findChild("content");
			if (contentElement != null) {
				this.content = new Content();
				this.content.setChildren(contentElement.getChildren());
				this.content.setAttributes(contentElement.getAttributes());
			}
			Element reasonElement = child.findChild("reason");
			if (reasonElement != null) {
				this.reason = new Reason();
				this.reason.setChildren(reasonElement.getChildren());
				this.reason.setAttributes(reasonElement.getAttributes());
			}
			this.jingle.setAttributes(child.getAttributes());
		}
		return child;
	}

	public JinglePacket setContent(Content content) {
		this.content = content;
		return this;
	}

	public Content getJingleContent() {
		if (this.content == null) {
			this.content = new Content();
		}
		return this.content;
	}

	public JinglePacket setReason(Reason reason) {
		this.reason = reason;
		return this;
	}

	public Reason getReason() {
		return this.reason;
	}

	private void build() {
		this.children.clear();
		this.jingle.clearChildren();
		this.jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
		if (this.content != null) {
			jingle.addChild(this.content);
		}
		if (this.reason != null) {
			jingle.addChild(this.reason);
		}
		this.children.add(jingle);
		this.setAttribute("type", "set");
	}

	public String getSessionId() {
		return this.jingle.getAttribute("sid");
	}

	public void setSessionId(String sid) {
		this.jingle.setAttribute("sid", sid);
	}

	@Override
	public String toString() {
		this.build();
		return super.toString();
	}

	public void setAction(String action) {
		this.jingle.setAttribute("action", action);
	}

	public String getAction() {
		return this.jingle.getAttribute("action");
	}

	public void setInitiator(final Jid initiator) {
		this.jingle.setAttribute("initiator", initiator.toString());
	}

	public boolean isAction(String action) {
		return action.equalsIgnoreCase(this.getAction());
	}
}
