package eu.siacs.conversations.xmpp.stanzas.jingle;

import java.io.File;
import java.util.List;

import eu.siacs.conversations.xml.Element;

public class Content extends Element {
	private Content(String name) {
		super(name);
	}
	
	public Content() {
		super("content");
	}

	public void offerFile(File actualFile) {
		Element description = this.addChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
		Element offer = description.addChild("offer");
		Element file = offer.addChild("file");
		file.addChild("size").setContent(""+actualFile.length());
		file.addChild("name").setContent(actualFile.getName());
	}

	public void setCanditates(List<Element> canditates) {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		if (transport==null) {
			transport = this.addChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		}
		transport.clearChildren();
		for(Element canditate : canditates) {
			transport.addChild(canditate);
		}
	}
}
