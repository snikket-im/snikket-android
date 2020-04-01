package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.support.annotation.NonNull;

import com.google.common.base.Preconditions;

import java.util.Locale;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class Content extends Element {
    private String transportId;


    //refactor to getDescription and getTransport
    //return either FileTransferDescription or GenericDescription or RtpDescription (all extend Description interface)

    public Content(final Creator creator, final String name) {
        super("content", Namespace.JINGLE);
        this.setAttribute("creator", creator.toString());
        this.setAttribute("name", name);
    }

    private Content() {
        super("content", Namespace.JINGLE);
    }

    public static Content upgrade(final Element element) {
        Preconditions.checkArgument("content".equals(element.getName()));
        final Content content = new Content();
        content.setAttributes(element.getAttributes());
        content.setChildren(element.getChildren());
        return content;
    }

    public Creator getCreator() {
        return Creator.of(getAttribute("creator"));
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

    public void setTransportId(String sid) {
        this.transportId = sid;
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

    public enum Creator {
        INITIATOR, RESPONDER;

        public static Creator of(final String value) {
            return Creator.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }

    public enum Senders {
        BOTH, INITIATOR, NONE, RESPONDER;

        public static Senders of(final String value) {
            return Senders.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @Override
        @NonNull
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }
    }
}
