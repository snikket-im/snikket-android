package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.CaseFormat;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Longs;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

import java.util.List;

public class FileTransferDescription extends GenericDescription {

    private FileTransferDescription() {
        super("description", Namespace.JINGLE_APPS_FILE_TRANSFER);
    }

    public static FileTransferDescription of(final File fileDescription) {
        final var description = new FileTransferDescription();
        final var file = description.addChild("file", Namespace.JINGLE_APPS_FILE_TRANSFER);
        file.addChild("name").setContent(fileDescription.name);
        file.addChild("size").setContent(Long.toString(fileDescription.size));
        if (fileDescription.mediaType != null) {
            file.addChild("mediaType").setContent(fileDescription.mediaType);
        }
        return description;
    }

    public File getFile() {
        final Element fileElement = this.findChild("file", Namespace.JINGLE_APPS_FILE_TRANSFER);
        if (fileElement == null) {
            Log.d(Config.LOGTAG,"no file? "+this);
            throw new IllegalStateException("file transfer description has no file");
        }
        final String name = fileElement.findChildContent("name");
        final String sizeAsString = fileElement.findChildContent("size");
        final String mediaType = fileElement.findChildContent("mediaType");
        if (Strings.isNullOrEmpty(name) || Strings.isNullOrEmpty(sizeAsString)) {
            throw new IllegalStateException("File definition is missing name and/or size");
        }
        final Long size = Longs.tryParse(sizeAsString);
        if (size == null) {
            throw new IllegalStateException("Invalid file size");
        }
        final List<Hash> hashes = findHashes(fileElement.getChildren());
        return new File(size, name, mediaType, hashes);
    }

    public static SessionInfo getSessionInfo(@NonNull final JinglePacket jinglePacket) {
        Preconditions.checkNotNull(jinglePacket);
        Preconditions.checkArgument(
                jinglePacket.getAction() == JinglePacket.Action.SESSION_INFO,
                "jingle packet is not a session-info");
        final Element jingle = jinglePacket.findChild("jingle", Namespace.JINGLE);
        if (jingle == null) {
            return null;
        }
        final Element checksum = jingle.findChild("checksum", Namespace.JINGLE_APPS_FILE_TRANSFER);
        if (checksum != null) {
            final Element file = checksum.findChild("file", Namespace.JINGLE_APPS_FILE_TRANSFER);
            final String name = checksum.getAttribute("name");
            if (file == null || Strings.isNullOrEmpty(name)) {
                return null;
            }
            return new Checksum(name, findHashes(file.getChildren()));
        }
        final Element received = jingle.findChild("received", Namespace.JINGLE_APPS_FILE_TRANSFER);
        if (received != null) {
            final String name = received.getAttribute("name");
            if (Strings.isNullOrEmpty(name)) {
                return new Received(name);
            }
        }
        return null;
    }

    private static List<Hash> findHashes(final List<Element> elements) {
        final ImmutableList.Builder<Hash> hashes = new ImmutableList.Builder<>();
        for (final Element child : elements) {
            if ("hash".equals(child.getName()) && Namespace.HASHES.equals(child.getNamespace())) {
                final Algorithm algorithm;
                try {
                    algorithm = Algorithm.of(child.getAttribute("algo"));
                } catch (final IllegalArgumentException e) {
                    continue;
                }
                final String content = child.getContent();
                if (Strings.isNullOrEmpty(content)) {
                    continue;
                }
                if (BaseEncoding.base64().canDecode(content)) {
                    hashes.add(new Hash(BaseEncoding.base64().decode(content), algorithm));
                }
            }
        }
        return hashes.build();
    }

    public static FileTransferDescription upgrade(final Element element) {
        Preconditions.checkArgument(
                "description".equals(element.getName()),
                "Name of provided element is not description");
        Preconditions.checkArgument(
                element.getNamespace().equals(Namespace.JINGLE_APPS_FILE_TRANSFER),
                "Element does not match a file transfer namespace");
        final FileTransferDescription description = new FileTransferDescription();
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }

    public static final class Checksum extends SessionInfo {
        public final List<Hash> hashes;

        public Checksum(final String name, List<Hash> hashes) {
            super(name);
            this.hashes = hashes;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this).add("hashes", hashes).toString();
        }

        @Override
        public Element asElement() {
            final var checksum = new Element("checksum", Namespace.JINGLE_APPS_FILE_TRANSFER);
            checksum.setAttribute("name", name);
            final var file = checksum.addChild("file", Namespace.JINGLE_APPS_FILE_TRANSFER);
            for (final Hash hash : hashes) {
                final var element = file.addChild("hash", Namespace.HASHES);
                element.setAttribute(
                        "algo",
                        CaseFormat.UPPER_UNDERSCORE.to(
                                CaseFormat.LOWER_HYPHEN, hash.algorithm.toString()));
                element.setContent(BaseEncoding.base64().encode(hash.hash));
            }
            return checksum;
        }
    }

    public static final class Received extends SessionInfo {

        public Received(String name) {
            super(name);
        }

        @Override
        public Element asElement() {
            final var element = new Element("received", Namespace.JINGLE_APPS_FILE_TRANSFER);
            element.setAttribute("name", name);
            return element;
        }
    }

    public abstract static sealed class SessionInfo permits Checksum, Received {

        public final String name;

        protected SessionInfo(final String name) {
            this.name = name;
        }

        public abstract Element asElement();
    }

    public static class File {
        public final long size;
        public final String name;
        public final String mediaType;

        public final List<Hash> hashes;

        public File(long size, String name, String mediaType, List<Hash> hashes) {
            this.size = size;
            this.name = name;
            this.mediaType = mediaType;
            this.hashes = hashes;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("size", size)
                    .add("name", name)
                    .add("mediaType", mediaType)
                    .add("hashes", hashes)
                    .toString();
        }
    }

    public static class Hash {
        public final byte[] hash;
        public final Algorithm algorithm;

        public Hash(byte[] hash, Algorithm algorithm) {
            this.hash = hash;
            this.algorithm = algorithm;
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("hash", hash)
                    .add("algorithm", algorithm)
                    .toString();
        }
    }

    public enum Algorithm {
        SHA_1,
        SHA_256;

        public static Algorithm of(final String value) {
            if (Strings.isNullOrEmpty(value)) {
                return null;
            }
            return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, value));
        }
    }
}
