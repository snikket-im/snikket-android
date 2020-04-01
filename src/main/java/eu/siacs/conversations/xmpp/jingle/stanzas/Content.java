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

    public Senders getSenders() {
        return Senders.of(getAttribute("senders"));
    }

    public void setSenders(Senders senders) {
        this.setAttribute("senders", senders.toString());
    }

    public GenericDescription getDescription() {
        final Element description = this.findChild("description");
        if (description == null) {
            return null;
        }
        final String xmlns = description.getNamespace();
        if (FileTransferDescription.NAMESPACES.contains(xmlns)) {
            return FileTransferDescription.upgrade(description);
        } else {
            return GenericDescription.upgrade(description);
        }
    }

    public void setDescription(final GenericDescription description) {
        Preconditions.checkNotNull(description);
        this.addChild(description);
    }

    public String getDescriptionNamespace() {
        final Element description = this.findChild("description");
        return description == null ? null : description.getNamespace();
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
