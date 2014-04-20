package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jingle.JingleFile;

public class Content extends Element {
	
	private String transportId;
	
	private Content(String name) {
		super(name);
	}
	
	public Content() {
		super("content");
	}

	public void setTransportId(String sid) {
		this.transportId = sid;
	}
	
	public void setFileOffer(JingleFile actualFile) {
		Element description = this.addChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
		Element offer = description.addChild("offer");
		Element file = offer.addChild("file");
		file.addChild("size").setContent(""+actualFile.getSize());
		file.addChild("name").setContent(actualFile.getName());
	}
	
	public Element getFileOffer() {
		Element description = this.findChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
		if (description==null) {
			return null;
		}
		Element offer = description.findChild("offer");
		if (offer==null) {
			return null;
		}
		return offer.findChild("file");
	}
	
	public void setFileOffer(Element fileOffer) {
		Element description = this.findChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
		if (description==null) {
			description = this.addChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
		}
		description.addChild(fileOffer);
	}
	
	public String getTransportId() {
		if (hasSocks5Transport()) {
			this.transportId = socks5transport().getAttribute("sid");
		} else if (hasIbbTransport()) {
			this.transportId = ibbTransport().getAttribute("sid");
		}
		return this.transportId;
	}
	
	public void setUsedCandidate(String cid) {
		socks5transport().clearChildren();
		Element usedCandidate = socks5transport().addChild("candidate-used");
		usedCandidate.setAttribute("cid",cid);
	}

	public void setCandidateError() {
		socks5transport().clearChildren();
		socks5transport().addChild("candidate-error");
	}
	
	public Element socks5transport() {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
		if (transport==null) {
			transport = this.addChild("transport", "urn:xmpp:jingle:transports:s5b:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}
	
	public Element ibbTransport() {
		Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:ibb:1");
		if (transport==null) {
			transport = this.addChild("transport", "urn:xmpp:jingle:transports:ibb:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}
	
	public boolean hasSocks5Transport() {
		return this.hasChild("transport", "urn:xmpp:jingle:transports:s5b:1");
	}
	
	public boolean hasIbbTransport() {
		return this.hasChild("transport","urn:xmpp:jingle:transports:ibb:1");
	}
}
