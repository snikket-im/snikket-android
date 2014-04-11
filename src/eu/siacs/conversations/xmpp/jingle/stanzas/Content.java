package eu.siacs.conversations.xmpp.jingle.stanzas;

import java.io.File;
import java.util.ArrayList;
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

	public void setCandidates(String transportId, List<Element> canditates) {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		if (transport==null) {
			transport = this.addChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		}
		transport.setAttribute("sid", transportId);
		transport.clearChildren();
		for(Element canditate : canditates) {
			transport.addChild(canditate);
		}
	}
	
	public List<Element> getCanditates() {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		if (transport==null) {
			return new ArrayList<Element>();
		} else {
			return transport.getChildren();
		}
	}
	
	public String getUsedCandidate() {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		if (transport==null) {
			return null;
		}
		Element usedCandidate = transport.findChild("candidate-used");
		if (usedCandidate==null) {
			return null;
		} else {
			return usedCandidate.getAttribute("cid");
		}
	}
}
