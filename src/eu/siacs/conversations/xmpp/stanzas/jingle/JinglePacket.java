package eu.siacs.conversations.xmpp.stanzas.jingle;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JinglePacket extends IqPacket {
	Content content = null;
	Reason reason = null;
	
	@Override
	public Element addChild(Element child) {
		if ("jingle".equals(child.getName())) {
			Element contentElement = child.findChild("content");
			if (contentElement!=null) {
				this.content = new Content();
				this.content.setChildren(contentElement.getChildren());
				this.content.setAttributes(contentElement.getAttributes());
			}
			Element reasonElement = child.findChild("reason");
			if (reasonElement!=null) {
				this.reason = new Reason();
				this.reason.setChildren(reasonElement.getChildren());
				this.reason.setAttributes(reasonElement.getAttributes());
			}
			this.build();
			this.findChild("jingle").setAttributes(child.getAttributes());
		}
		return child;
	}
	
	public JinglePacket setContent(Content content) {
		this.content = content;
		this.build();
		return this;
	}
	
	public JinglePacket setReason(Reason reason) {
		this.reason = reason;
		this.build();
		return this;
	}
	
	private void build() {
		this.children.clear();
		Element jingle = addChild("jingle", "urn:xmpp:jingle:1");
		if (this.content!=null) {
			jingle.addChild(this.content);
		}
		if (this.reason != null) {
			jingle.addChild(this.reason);
		}
		this.children.add(jingle);
	}
}
