package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import java.util.Arrays;
import java.util.List;

import eu.siacs.conversations.crypto.axolotl.XmppAxolotlMessage;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.xml.Element;

public class FileTransferDescription extends GenericDescription {

    public static List<String> NAMESPACES = Arrays.asList(
            Version.FT_3.namespace,
            Version.FT_4.namespace,
            Version.FT_5.namespace
    );


    private FileTransferDescription(String name, String namespace) {
        super(name, namespace);
    }

    public Version getVersion() {
        final String namespace = getNamespace();
        if (namespace.equals(Version.FT_3.namespace)) {
            return Version.FT_3;
        } else if (namespace.equals(Version.FT_4.namespace)) {
            return Version.FT_4;
        } else if (namespace.equals(Version.FT_5.namespace)) {
            return Version.FT_5;
        } else {
            throw new IllegalStateException("Unknown namespace");
        }
    }

    public Element getFileOffer() {
        final Version version = getVersion();
        if (version == Version.FT_3) {
            final Element offer = this.findChild("offer");
            return offer == null ? null : offer.findChild("file");
        } else {
            return this.findChild("file");
        }
    }

    public static FileTransferDescription of(DownloadableFile file, Version version, XmppAxolotlMessage axolotlMessage) {
        final FileTransferDescription description = new FileTransferDescription("description", version.getNamespace());
        final Element fileElement;
        if (version == Version.FT_3) {
            Element offer = description.addChild("offer");
            fileElement = offer.addChild("file");
        } else {
            fileElement = description.addChild("file");
        }
        fileElement.addChild("size").setContent(Long.toString(file.getExpectedSize()));
        fileElement.addChild("name").setContent(file.getName());
        if (axolotlMessage != null) {
            fileElement.addChild(axolotlMessage.toElement());
        }
        return description;
    }

    public static FileTransferDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()), "Name of provided element is not description");
        Preconditions.checkArgument(NAMESPACES.contains(element.getNamespace()), "Element does not match a file transfer namespace");
        final FileTransferDescription description = new FileTransferDescription("description", element.getNamespace());
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
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
}
