package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Content extends Element {

    public enum Version {
        FT_3("urn:xmpp:jingle:apps:file-transfer:3"),
        FT_4("urn:xmpp:jingle:apps:file-transfer:4"),
        FT_5("urn:xmpp:jingle:apps:file-transfer:5");

        private final String namespace;

        Version(String namespace) {
            this.namespace = namespace;
        }

        public String getNamespace() {
            return namespace;
        }
    }

    private String transportId;

    public Content() {
        super("content");
    }

    public Content(String creator, String name) {
        super("content");
        this.setAttribute("creator", creator);
        this.setAttribute("senders", creator);
        this.setAttribute("name", name);
    }

    public Version getVersion() {
        if (hasChild("description", Version.FT_3.namespace)) {
            return Version.FT_3;
        } else if (hasChild("description", Version.FT_4.namespace)) {
            return Version.FT_4;
        } else if (hasChild("description", Version.FT_5.namespace)) {
            return Version.FT_5;
        }
        return null;
    }

    public void setTransportId(String sid) {
        this.transportId = sid;
    }

    public Element setFileOffer(DownloadableFile actualFile, boolean otr, Version version) {
        Element description = this.addChild("description", version.namespace);
        Element file;
        if (version == Version.FT_3) {
            Element offer = description.addChild("offer");
            file = offer.addChild("file");
        } else {
            file = description.addChild("file");
        }
        file.addChild("size").setContent(Long.toString(actualFile.getExpectedSize()));
        if (otr) {
            file.addChild("name").setContent(actualFile.getName() + ".otr");
        } else {
            file.addChild("name").setContent(actualFile.getName());
        }
        return file;
    }

    public Element getFileOffer(Version version) {
        Element description = this.findChild("description", version.namespace);
        if (description == null) {
            return null;
        }
        if (version == Version.FT_3) {
            Element offer = description.findChild("offer");
            if (offer == null) {
                return null;
            }
            return offer.findChild("file");
        } else {
            return description.findChild("file");
        }
    }

    public void setFileOffer(Element fileOffer, Version version) {
        Element description = this.addChild("description", version.namespace);
        if (version == Version.FT_3) {
            description.addChild("offer").addChild(fileOffer);
        } else {
            description.addChild(fileOffer);
        }
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
        Element transport = this.findChild("transport", Namespace.JINGLE_TRANSPORTS_S5B);
        if (transport == null) {
            transport = this.addChild("transport", Namespace.JINGLE_TRANSPORTS_S5B);
            transport.setAttribute("sid", this.transportId);
        }
        return transport;
    }

    public Element ibbTransport() {
        Element transport = this.findChild("transport", Namespace.JINGLE_TRANSPORTS_IBB);
        if (transport == null) {
            transport = this.addChild("transport", Namespace.JINGLE_TRANSPORTS_IBB);
            transport.setAttribute("sid", this.transportId);
        }
        return transport;
    }

    public boolean hasSocks5Transport() {
        return this.hasChild("transport", Namespace.JINGLE_TRANSPORTS_S5B);
    }

    public boolean hasIbbTransport() {
        return this.hasChild("transport", Namespace.JINGLE_TRANSPORTS_IBB);
    }
}
