package eu.siacs.conversations.xmpp.jingle;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IbbTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.SocksByteStreamsTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.WebRTCDataChannelTransportInfo;
import eu.siacs.conversations.xmpp.jingle.transports.Transport;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FileTransferContentMap
        extends AbstractContentMap<FileTransferDescription, GenericTransportInfo> {

    private static final List<Class<? extends GenericTransportInfo>> SUPPORTED_TRANSPORTS =
            Arrays.asList(
                    SocksByteStreamsTransportInfo.class,
                    IbbTransportInfo.class,
                    WebRTCDataChannelTransportInfo.class);

    protected FileTransferContentMap(
            final Group group, final Map<String, DescriptionTransport<FileTransferDescription, GenericTransportInfo>>
                    contents) {
        super(group, contents);
    }

    public static FileTransferContentMap of(final JinglePacket jinglePacket) {
        final Map<String, DescriptionTransport<FileTransferDescription, GenericTransportInfo>>
                contents = of(jinglePacket.getJingleContents());
        return new FileTransferContentMap(jinglePacket.getGroup(), contents);
    }

    public static DescriptionTransport<FileTransferDescription, GenericTransportInfo> of(
            final Content content) {
        final GenericDescription description = content.getDescription();
        final GenericTransportInfo transportInfo = content.getTransport();
        final Content.Senders senders = content.getSenders();
        final FileTransferDescription fileTransferDescription;
        if (description == null) {
            fileTransferDescription = null;
        } else if (description instanceof FileTransferDescription ftDescription) {
            fileTransferDescription = ftDescription;
        } else {
            throw new UnsupportedApplicationException(
                    "Content does not contain file transfer description");
        }
        if (!SUPPORTED_TRANSPORTS.contains(transportInfo.getClass())) {
            throw new UnsupportedTransportException("Content does not have supported transport");
        }
        return new DescriptionTransport<>(senders, fileTransferDescription, transportInfo);
    }

    private static Map<String, DescriptionTransport<FileTransferDescription, GenericTransportInfo>>
            of(final Map<String, Content> contents) {
        return ImmutableMap.copyOf(
                Maps.transformValues(contents, content -> content == null ? null : of(content)));
    }

    public static FileTransferContentMap of(
            final FileTransferDescription.File file, final Transport.InitialTransportInfo initialTransportInfo) {
        // TODO copy groups
        final var transportInfo = initialTransportInfo.transportInfo;
        return new FileTransferContentMap(initialTransportInfo.group,
                Map.of(
                        initialTransportInfo.contentName,
                        new DescriptionTransport<>(
                                Content.Senders.INITIATOR,
                                FileTransferDescription.of(file),
                                transportInfo)));
    }

    public FileTransferDescription.File requireOnlyFile() {
        if (this.contents.size() != 1) {
            throw new IllegalStateException("Only one file at a time is supported");
        }
        final var dt = Iterables.getOnlyElement(this.contents.values());
        return dt.description.getFile();
    }

    public FileTransferDescription requireOnlyFileTransferDescription() {
        if (this.contents.size() != 1) {
            throw new IllegalStateException("Only one file at a time is supported");
        }
        final var dt = Iterables.getOnlyElement(this.contents.values());
        return dt.description;
    }

    public GenericTransportInfo requireOnlyTransportInfo() {
        if (this.contents.size() != 1) {
            throw new IllegalStateException(
                    "We expect exactly one content with one transport info");
        }
        final var dt = Iterables.getOnlyElement(this.contents.values());
        return dt.transport;
    }

    public FileTransferContentMap withTransport(final Transport.TransportInfo transportWrapper) {
        final var transportInfo = transportWrapper.transportInfo;
        return new FileTransferContentMap(transportWrapper.group,
                ImmutableMap.copyOf(
                        Maps.transformValues(
                                contents,
                                content -> {
                                    if (content == null) {
                                        return null;
                                    }
                                    return new DescriptionTransport<>(
                                            content.senders, content.description, transportInfo);
                                })));
    }

    public FileTransferContentMap candidateUsed(final String streamId, final String cid) {
        return new FileTransferContentMap(null,
                ImmutableMap.copyOf(
                        Maps.transformValues(
                                contents,
                                content -> {
                                    if (content == null) {
                                        return null;
                                    }
                                    final var transportInfo =
                                            new SocksByteStreamsTransportInfo(
                                                    streamId, Collections.emptyList());
                                    final Element candidateUsed =
                                            transportInfo.addChild(
                                                    "candidate-used",
                                                    Namespace.JINGLE_TRANSPORTS_S5B);
                                    candidateUsed.setAttribute("cid", cid);
                                    return new DescriptionTransport<>(
                                            content.senders, null, transportInfo);
                                })));
    }

    public FileTransferContentMap candidateError(final String streamId) {
        return new FileTransferContentMap(null,
                ImmutableMap.copyOf(
                        Maps.transformValues(
                                contents,
                                content -> {
                                    if (content == null) {
                                        return null;
                                    }
                                    final var transportInfo =
                                            new SocksByteStreamsTransportInfo(
                                                    streamId, Collections.emptyList());
                                    transportInfo.addChild(
                                            "candidate-error", Namespace.JINGLE_TRANSPORTS_S5B);
                                    return new DescriptionTransport<>(
                                            content.senders, null, transportInfo);
                                })));
    }

    public FileTransferContentMap proxyActivated(final String streamId, final String cid) {
        return new FileTransferContentMap(null,
                ImmutableMap.copyOf(
                        Maps.transformValues(
                                contents,
                                content -> {
                                    if (content == null) {
                                        return null;
                                    }
                                    final var transportInfo =
                                            new SocksByteStreamsTransportInfo(
                                                    streamId, Collections.emptyList());
                                    final Element candidateUsed =
                                            transportInfo.addChild(
                                                    "activated", Namespace.JINGLE_TRANSPORTS_S5B);
                                    candidateUsed.setAttribute("cid", cid);
                                    return new DescriptionTransport<>(
                                            content.senders, null, transportInfo);
                                })));
    }

    FileTransferContentMap transportInfo() {
        return new FileTransferContentMap(this.group,
                Maps.transformValues(
                        contents,
                        dt -> new DescriptionTransport<>(dt.senders, null, dt.transport)));
    }

    FileTransferContentMap transportInfo(
            final String contentName, final IceUdpTransportInfo.Candidate candidate) {
        final DescriptionTransport<FileTransferDescription, GenericTransportInfo> descriptionTransport =
                contents.get(contentName);
        if (descriptionTransport == null) {
            throw new IllegalArgumentException(
                    "Unable to find transport info for content name " + contentName);
        }
        final WebRTCDataChannelTransportInfo transportInfo;
        if (descriptionTransport.transport instanceof WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo) {
            transportInfo = webRTCDataChannelTransportInfo;
        } else {
            throw new IllegalStateException("TransportInfo is not WebRTCDataChannel");
        }
        final WebRTCDataChannelTransportInfo newTransportInfo = transportInfo.cloneWrapper();
        newTransportInfo.addCandidate(candidate);
        return new FileTransferContentMap(
                null,
                ImmutableMap.of(
                        contentName,
                        new DescriptionTransport<>(
                                descriptionTransport.senders, null, newTransportInfo)));
    }
}
