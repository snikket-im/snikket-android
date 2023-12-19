package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.FileTransferDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.GenericTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;
import eu.siacs.conversations.xmpp.jingle.stanzas.WebRTCDataChannelTransportInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SessionDescription {

    public static final String LINE_DIVIDER = "\r\n";
    private static final String HARDCODED_MEDIA_PROTOCOL =
            "UDP/TLS/RTP/SAVPF"; // probably only true for DTLS-SRTP aka when we have a fingerprint
    private static final String HARDCODED_APPLICATION_PROTOCOL = "UDP/DTLS/SCTP";
    private static final String FORMAT_WEBRTC_DATA_CHANNEL = "webrtc-datachannel";
    private static final int HARDCODED_MEDIA_PORT = 9;
    private static final Collection<String> HARDCODED_ICE_OPTIONS =
            Collections.singleton("trickle");
    private static final String HARDCODED_CONNECTION = "IN IP4 0.0.0.0";

    public final int version;
    public final String name;
    public final String connectionData;
    public final ArrayListMultimap<String, String> attributes;
    public final List<Media> media;

    public SessionDescription(
            int version,
            String name,
            String connectionData,
            ArrayListMultimap<String, String> attributes,
            List<Media> media) {
        this.version = version;
        this.name = name;
        this.connectionData = connectionData;
        this.attributes = attributes;
        this.media = media;
    }

    private static void appendAttributes(StringBuilder s, Multimap<String, String> attributes) {
        for (final Map.Entry<String, String> attribute : attributes.entries()) {
            final String key = attribute.getKey();
            final String value = attribute.getValue();
            s.append("a=").append(key);
            if (!Strings.isNullOrEmpty(value)) {
                s.append(':').append(value);
            }
            s.append(LINE_DIVIDER);
        }
    }

    public static SessionDescription parse(final String input) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        MediaBuilder currentMediaBuilder = null;
        ArrayListMultimap<String, String> attributeMap = ArrayListMultimap.create();
        ImmutableList.Builder<Media> mediaBuilder = new ImmutableList.Builder<>();
        for (final String line : input.split(LINE_DIVIDER)) {
            final String[] pair = line.trim().split("=", 2);
            if (pair.length < 2 || pair[0].length() != 1) {
                Log.d(Config.LOGTAG, "skipping sdp parsing on line " + line);
                continue;
            }
            final char key = pair[0].charAt(0);
            final String value = pair[1];
            switch (key) {
                case 'v' -> sessionDescriptionBuilder.setVersion(ignorantIntParser(value));
                case 'c' -> {
                    if (currentMediaBuilder != null) {
                        currentMediaBuilder.setConnectionData(value);
                    } else {
                        sessionDescriptionBuilder.setConnectionData(value);
                    }
                }
                case 's' -> sessionDescriptionBuilder.setName(value);
                case 'a' -> {
                    final Pair<String, String> attribute = parseAttribute(value);
                    attributeMap.put(attribute.first, attribute.second);
                }
                case 'm' -> {
                    if (currentMediaBuilder == null) {
                        sessionDescriptionBuilder.setAttributes(attributeMap);
                    } else {
                        currentMediaBuilder.setAttributes(attributeMap);
                        mediaBuilder.add(currentMediaBuilder.createMedia());
                    }
                    attributeMap = ArrayListMultimap.create();
                    currentMediaBuilder = new MediaBuilder();
                    final String[] parts = value.split(" ");
                    if (parts.length >= 3) {
                        currentMediaBuilder.setMedia(parts[0]);
                        currentMediaBuilder.setPort(ignorantIntParser(parts[1]));
                        currentMediaBuilder.setProtocol(parts[2]);
                        ImmutableList.Builder<Integer> formats = new ImmutableList.Builder<>();
                        for (int i = 3; i < parts.length; ++i) {
                            formats.add(ignorantIntParser(parts[i]));
                        }
                        currentMediaBuilder.setFormats(formats.build());
                    } else {
                        Log.d(Config.LOGTAG, "skipping media line " + line);
                    }
                }
            }
        }
        if (currentMediaBuilder != null) {
            currentMediaBuilder.setAttributes(attributeMap);
            mediaBuilder.add(currentMediaBuilder.createMedia());
        } else {
            sessionDescriptionBuilder.setAttributes(attributeMap);
        }
        sessionDescriptionBuilder.setMedia(mediaBuilder.build());
        return sessionDescriptionBuilder.createSessionDescription();
    }

    public static SessionDescription of(final FileTransferContentMap contentMap) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        final ArrayListMultimap<String, String> attributeMap = ArrayListMultimap.create();
        final ImmutableList.Builder<Media> mediaListBuilder = new ImmutableList.Builder<>();

        final Group group = contentMap.group;
        if (group != null) {
            final String semantics = group.getSemantics();
            checkNoWhitespace(semantics, "group semantics value must not contain any whitespace");
            final var idTags = group.getIdentificationTags();
            for (final String content : idTags) {
                checkNoWhitespace(content, "group content names must not contain any whitespace");
            }
            attributeMap.put("group", group.getSemantics() + " " + Joiner.on(' ').join(idTags));
        }

        // TODO my-media-stream can be removed I think
        attributeMap.put("msid-semantic", " WMS my-media-stream");

        for (final Map.Entry<
                        String, DescriptionTransport<FileTransferDescription, GenericTransportInfo>>
                entry : contentMap.contents.entrySet()) {
            final var dt = entry.getValue();
            final WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo;
            if (dt.transport instanceof WebRTCDataChannelTransportInfo transportInfo) {
                webRTCDataChannelTransportInfo = transportInfo;
            } else {
                throw new IllegalArgumentException("Transport is not of type WebRTCDataChannel");
            }
            final String name = entry.getKey();
            checkNoWhitespace(name, "content name must not contain any whitespace");

            final MediaBuilder mediaBuilder = new MediaBuilder();
            mediaBuilder.setMedia("application");
            mediaBuilder.setConnectionData(HARDCODED_CONNECTION);
            mediaBuilder.setPort(HARDCODED_MEDIA_PORT);
            mediaBuilder.setProtocol(HARDCODED_APPLICATION_PROTOCOL);
            mediaBuilder.setAttributes(
                    transportInfoMediaAttributes(webRTCDataChannelTransportInfo));
            mediaBuilder.setFormat(FORMAT_WEBRTC_DATA_CHANNEL);
            mediaListBuilder.add(mediaBuilder.createMedia());
        }

        sessionDescriptionBuilder.setVersion(0);
        sessionDescriptionBuilder.setName("-");
        sessionDescriptionBuilder.setMedia(mediaListBuilder.build());
        sessionDescriptionBuilder.setAttributes(attributeMap);
        return sessionDescriptionBuilder.createSessionDescription();
    }

    public static SessionDescription of(
            final RtpContentMap contentMap, final boolean isInitiatorContentMap) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        final ArrayListMultimap<String, String> attributeMap = ArrayListMultimap.create();
        final ImmutableList.Builder<Media> mediaListBuilder = new ImmutableList.Builder<>();
        final Group group = contentMap.group;
        if (group != null) {
            final String semantics = group.getSemantics();
            checkNoWhitespace(semantics, "group semantics value must not contain any whitespace");
            final var idTags = group.getIdentificationTags();
            for (final String content : idTags) {
                checkNoWhitespace(content, "group content names must not contain any whitespace");
            }
            attributeMap.put("group", group.getSemantics() + " " + Joiner.on(' ').join(idTags));
        }

        // TODO my-media-stream can be removed I think
        attributeMap.put("msid-semantic", " WMS my-media-stream");

        for (final Map.Entry<String, DescriptionTransport<RtpDescription, IceUdpTransportInfo>>
                entry : contentMap.contents.entrySet()) {
            final String name = entry.getKey();
            checkNoWhitespace(name, "content name must not contain any whitespace");
            final DescriptionTransport<RtpDescription, IceUdpTransportInfo> descriptionTransport =
                    entry.getValue();
            final RtpDescription description = descriptionTransport.description;
            final ArrayListMultimap<String, String> mediaAttributes = ArrayListMultimap.create();
            mediaAttributes.putAll(transportInfoMediaAttributes(descriptionTransport.transport));
            final ImmutableList.Builder<Integer> formatBuilder = new ImmutableList.Builder<>();
            for (final RtpDescription.PayloadType payloadType : description.getPayloadTypes()) {
                final String id = payloadType.getId();
                if (Strings.isNullOrEmpty(id)) {
                    throw new IllegalArgumentException("Payload type is missing id");
                }
                if (!isInt(id)) {
                    throw new IllegalArgumentException("Payload id is not numeric");
                }
                formatBuilder.add(payloadType.getIntId());
                mediaAttributes.put("rtpmap", payloadType.toSdpAttribute());
                final List<RtpDescription.Parameter> parameters = payloadType.getParameters();
                if (parameters.size() == 1) {
                    mediaAttributes.put(
                            "fmtp", RtpDescription.Parameter.toSdpString(id, parameters.get(0)));
                } else if (parameters.size() > 0) {
                    mediaAttributes.put(
                            "fmtp", RtpDescription.Parameter.toSdpString(id, parameters));
                }
                for (RtpDescription.FeedbackNegotiation feedbackNegotiation :
                        payloadType.getFeedbackNegotiations()) {
                    final String type = feedbackNegotiation.getType();
                    final String subtype = feedbackNegotiation.getSubType();
                    if (Strings.isNullOrEmpty(type)) {
                        throw new IllegalArgumentException(
                                "a feedback for payload-type "
                                        + id
                                        + " negotiation is missing type");
                    }
                    checkNoWhitespace(
                            type, "feedback negotiation type must not contain whitespace");
                    if (Strings.isNullOrEmpty(subtype)) {
                        mediaAttributes.put("rtcp-fb", id + " " + type);
                    } else {
                        checkNoWhitespace(
                                subtype,
                                "feedback negotiation subtype must not contain whitespace");
                        mediaAttributes.put("rtcp-fb", id + " " + type + " " + subtype);
                    }
                }
                for (RtpDescription.FeedbackNegotiationTrrInt feedbackNegotiationTrrInt :
                        payloadType.feedbackNegotiationTrrInts()) {
                    mediaAttributes.put(
                            "rtcp-fb", id + " trr-int " + feedbackNegotiationTrrInt.getValue());
                }
            }

            for (RtpDescription.FeedbackNegotiation feedbackNegotiation :
                    description.getFeedbackNegotiations()) {
                final String type = feedbackNegotiation.getType();
                final String subtype = feedbackNegotiation.getSubType();
                if (Strings.isNullOrEmpty(type)) {
                    throw new IllegalArgumentException("a feedback negotiation is missing type");
                }
                checkNoWhitespace(type, "feedback negotiation type must not contain whitespace");
                if (Strings.isNullOrEmpty(subtype)) {
                    mediaAttributes.put("rtcp-fb", "* " + type);
                } else {
                    checkNoWhitespace(
                            subtype, "feedback negotiation subtype must not contain whitespace");
                    mediaAttributes.put("rtcp-fb", "* " + type + " " + subtype); /**/
                }
            }
            for (final RtpDescription.FeedbackNegotiationTrrInt feedbackNegotiationTrrInt :
                    description.feedbackNegotiationTrrInts()) {
                mediaAttributes.put("rtcp-fb", "* trr-int " + feedbackNegotiationTrrInt.getValue());
            }
            for (final RtpDescription.RtpHeaderExtension extension :
                    description.getHeaderExtensions()) {
                final String id = extension.getId();
                final String uri = extension.getUri();
                if (Strings.isNullOrEmpty(id)) {
                    throw new IllegalArgumentException("A header extension is missing id");
                }
                checkNoWhitespace(id, "header extension id must not contain whitespace");
                if (Strings.isNullOrEmpty(uri)) {
                    throw new IllegalArgumentException("A header extension is missing uri");
                }
                checkNoWhitespace(uri, "feedback negotiation uri must not contain whitespace");
                mediaAttributes.put("extmap", id + " " + uri);
            }

            if (description.hasChild(
                    "extmap-allow-mixed", Namespace.JINGLE_RTP_HEADER_EXTENSIONS)) {
                mediaAttributes.put("extmap-allow-mixed", "");
            }

            for (final RtpDescription.SourceGroup sourceGroup : description.getSourceGroups()) {
                final String semantics = sourceGroup.getSemantics();
                final List<String> groups = sourceGroup.getSsrcs();
                if (Strings.isNullOrEmpty(semantics)) {
                    throw new IllegalArgumentException(
                            "A SSRC group is missing semantics attribute");
                }
                checkNoWhitespace(semantics, "source group semantics must not contain whitespace");
                if (groups.size() == 0) {
                    throw new IllegalArgumentException("A SSRC group is missing SSRC ids");
                }
                for (final String source : groups) {
                    checkNoWhitespace(source, "Sources must not contain whitespace");
                }
                mediaAttributes.put(
                        "ssrc-group",
                        String.format("%s %s", semantics, Joiner.on(' ').join(groups)));
            }
            for (final RtpDescription.Source source : description.getSources()) {
                for (final RtpDescription.Source.Parameter parameter : source.getParameters()) {
                    final String id = source.getSsrcId();
                    final String parameterName = parameter.getParameterName();
                    final String parameterValue = parameter.getParameterValue();
                    if (Strings.isNullOrEmpty(id)) {
                        throw new IllegalArgumentException(
                                "A source specific media attribute is missing the id");
                    }
                    checkNoWhitespace(
                            id, "A source specific media attributes must not contain whitespaces");
                    if (Strings.isNullOrEmpty(parameterName)) {
                        throw new IllegalArgumentException(
                                "A source specific media attribute is missing its name");
                    }
                    if (Strings.isNullOrEmpty(parameterValue)) {
                        throw new IllegalArgumentException(
                                "A source specific media attribute is missing its value");
                    }
                    checkNoWhitespace(
                            parameterName,
                            "A source specific media attribute name not not contain whitespace");
                    checkNoNewline(
                            parameterValue,
                            "A source specific media attribute value must not contain new lines");
                    mediaAttributes.put(
                            "ssrc", id + " " + parameterName + ":" + parameterValue.trim());
                }
            }

            mediaAttributes.put("mid", name);

            mediaAttributes.put(
                    descriptionTransport.senders.asMediaAttribute(isInitiatorContentMap), "");
            if (description.hasChild("rtcp-mux", Namespace.JINGLE_APPS_RTP) || group != null) {
                mediaAttributes.put("rtcp-mux", "");
            }

            // random additional attributes
            mediaAttributes.put("rtcp", "9 IN IP4 0.0.0.0");

            final MediaBuilder mediaBuilder = new MediaBuilder();
            mediaBuilder.setMedia(description.getMedia().toString().toLowerCase(Locale.ROOT));
            mediaBuilder.setConnectionData(HARDCODED_CONNECTION);
            mediaBuilder.setPort(HARDCODED_MEDIA_PORT);
            mediaBuilder.setProtocol(HARDCODED_MEDIA_PROTOCOL);
            mediaBuilder.setAttributes(mediaAttributes);
            mediaBuilder.setFormats(formatBuilder.build());
            mediaListBuilder.add(mediaBuilder.createMedia());
        }
        sessionDescriptionBuilder.setVersion(0);
        sessionDescriptionBuilder.setName("-");
        sessionDescriptionBuilder.setMedia(mediaListBuilder.build());
        sessionDescriptionBuilder.setAttributes(attributeMap);

        return sessionDescriptionBuilder.createSessionDescription();
    }

    private static Multimap<String, String> transportInfoMediaAttributes(
            final IceUdpTransportInfo transport) {
        final ArrayListMultimap<String, String> mediaAttributes = ArrayListMultimap.create();
        final String ufrag = transport.getAttribute("ufrag");
        final String pwd = transport.getAttribute("pwd");
        if (Strings.isNullOrEmpty(ufrag)) {
            throw new IllegalArgumentException(
                    "Transport element is missing required ufrag attribute");
        }
        checkNoWhitespace(ufrag, "ufrag value must not contain any whitespaces");
        mediaAttributes.put("ice-ufrag", ufrag);
        if (Strings.isNullOrEmpty(pwd)) {
            throw new IllegalArgumentException(
                    "Transport element is missing required pwd attribute");
        }
        checkNoWhitespace(pwd, "pwd value must not contain any whitespaces");
        mediaAttributes.put("ice-pwd", pwd);
        final List<String> negotiatedIceOptions = transport.getIceOptions();
        final Collection<String> iceOptions =
                negotiatedIceOptions.isEmpty() ? HARDCODED_ICE_OPTIONS : negotiatedIceOptions;
        mediaAttributes.put("ice-options", Joiner.on(' ').join(iceOptions));
        final IceUdpTransportInfo.Fingerprint fingerprint = transport.getFingerprint();
        if (fingerprint != null) {
            final String hashFunction = fingerprint.getHash();
            final String hash = fingerprint.getContent();
            if (Strings.isNullOrEmpty(hashFunction) || Strings.isNullOrEmpty(hash)) {
                throw new IllegalArgumentException("DTLS-SRTP missing hash");
            }
            checkNoWhitespace(hashFunction, "DTLS-SRTP hash function must not contain whitespace");
            checkNoWhitespace(hash, "DTLS-SRTP hash must not contain whitespace");
            mediaAttributes.put("fingerprint", hashFunction + " " + hash);
            final IceUdpTransportInfo.Setup setup = fingerprint.getSetup();
            if (setup != null) {
                mediaAttributes.put("setup", setup.toString().toLowerCase(Locale.ROOT));
            }
        }
        return ImmutableMultimap.copyOf(mediaAttributes);
    }

    private static Multimap<String, String> transportInfoMediaAttributes(
            final WebRTCDataChannelTransportInfo transport) {
        final ArrayListMultimap<String, String> mediaAttributes = ArrayListMultimap.create();
        final var iceUdpTransportInfo = transport.innerIceUdpTransportInfo();
        if (iceUdpTransportInfo == null) {
            throw new IllegalArgumentException(
                    "Transport element is missing inner ice-udp transport");
        }
        mediaAttributes.putAll(transportInfoMediaAttributes(iceUdpTransportInfo));
        final Integer sctpPort = transport.getSctpPort();
        if (sctpPort == null) {
            throw new IllegalArgumentException(
                    "Transport element is missing required sctp-port attribute");
        }
        mediaAttributes.put("sctp-port", String.valueOf(sctpPort));
        final Integer maxMessageSize = transport.getMaxMessageSize();
        if (maxMessageSize == null) {
            throw new IllegalArgumentException(
                    "Transport element is missing required max-message-size");
        }
        mediaAttributes.put("max-message-size", String.valueOf(maxMessageSize));
        return ImmutableMultimap.copyOf(mediaAttributes);
    }

    public static String checkNoWhitespace(final String input, final String message) {
        if (CharMatcher.whitespace().matchesAnyOf(input)) {
            throw new IllegalArgumentException(message);
        }
        return input;
    }

    public static String checkNoNewline(final String input, final String message) {
        if (CharMatcher.anyOf("\r\n").matchesAnyOf(message)) {
            throw new IllegalArgumentException(message);
        }
        return input;
    }

    public static int ignorantIntParser(final String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isInt(final String input) {
        if (input == null) {
            return false;
        }
        try {
            Integer.parseInt(input);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static Pair<String, String> parseAttribute(final String input) {
        final String[] pair = input.split(":", 2);
        if (pair.length == 2) {
            return new Pair<>(pair[0], pair[1]);
        } else {
            return new Pair<>(pair[0], "");
        }
    }

    @NonNull
    @Override
    public String toString() {
        final StringBuilder s =
                new StringBuilder()
                        .append("v=")
                        .append(version)
                        .append(LINE_DIVIDER)
                        // TODO randomize or static
                        .append("o=- 8770656990916039506 2 IN IP4 127.0.0.1")
                        .append(LINE_DIVIDER) // what ever that means
                        .append("s=")
                        .append(name)
                        .append(LINE_DIVIDER)
                        .append("t=0 0")
                        .append(LINE_DIVIDER);
        appendAttributes(s, attributes);
        for (Media media : this.media) {
            s.append("m=")
                    .append(media.media)
                    .append(' ')
                    .append(media.port)
                    .append(' ')
                    .append(media.protocol)
                    .append(' ')
                    .append(media.format)
                    .append(LINE_DIVIDER);
            s.append("c=").append(media.connectionData).append(LINE_DIVIDER);
            appendAttributes(s, media.attributes);
        }
        return s.toString();
    }

    public static class Media {
        public final String media;
        public final int port;
        public final String protocol;
        public final String format;
        public final String connectionData;
        public final Multimap<String, String> attributes;

        public Media(
                String media,
                int port,
                String protocol,
                String format,
                String connectionData,
                Multimap<String, String> attributes) {
            this.media = media;
            this.port = port;
            this.protocol = protocol;
            this.format = format;
            this.connectionData = connectionData;
            this.attributes = attributes;
        }
    }
}
