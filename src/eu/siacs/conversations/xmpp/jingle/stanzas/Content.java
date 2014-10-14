package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;

public class Content extends Element {

	private String transportId;

	private Content(String name) {
		super(name);
	}

	public Content() {
		super("content");
	}

	public Content(String creator, String name) {
		super("content");
		this.setAttribute("creator", creator);
		this.setAttribute("name", name);
	}

	public void setTransportId(String sid) {
		this.transportId = sid;
	}

	public void setFileOffer(DownloadableFile actualFile, boolean otr) {
		Element description = this.addChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		Element offer = description.addChild("offer");
		Element file = offer.addChild("file");
		file.addChild("size").setContent(Long.toString(actualFile.getSize()));
		if (otr) {
			file.addChild("name").setContent(actualFile.getName() + ".otr");
		} else {
			file.addChild("name").setContent(actualFile.getName());
		}
	}

	public Element getFileOffer() {
		Element description = this.findChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		if (description == null) {
			return null;
		}
		Element offer = description.findChild("offer");
		if (offer == null) {
			return null;
		}
		return offer.findChild("file");
	}

	public void setFileOffer(Element fileOffer) {
		Element description = this.findChild("description",
				"urn:xmpp:jingle:apps:file-transfer:3");
		if (description == null) {
			description = this.addChild("description",
					"urn:xmpp:jingle:apps:file-transfer:3");
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

	public Element socks5transport() {
		Element transport = this.findChild("transport",
				"urn:xmpp:jingle:transports:s5b:1");
		if (transport == null) {
			transport = this.addChild("transport",
					"urn:xmpp:jingle:transports:s5b:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}

	public Element ibbTransport() {
		Element transport = this.findChild("transport",
				"urn:xmpp:jingle:transports:ibb:1");
		if (transport == null) {
			transport = this.addChild("transport",
					"urn:xmpp:jingle:transports:ibb:1");
			transport.setAttribute("sid", this.transportId);
		}
		return transport;
	}

	public boolean hasSocks5Transport() {
		return this.hasChild("transport", "urn:xmpp:jingle:transports:s5b:1");
	}

	public boolean hasIbbTransport() {
		return this.hasChild("transport", "urn:xmpp:jingle:transports:ibb:1");
	}
}
