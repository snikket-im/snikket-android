package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;
import android.util.Pair;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.RtpDescription;

public class SessionDescription {

    private final static String LINE_DIVIDER = "\r\n";
    private final static String HARDCODED_MEDIA_PROTOCOL = "UDP/TLS/RTP/SAVPF"; //probably only true for DTLS-SRTP aka when we have a fingerprint
    private final static int HARDCODED_MEDIA_PORT = 9;
    private final static String HARDCODED_ICE_OPTIONS = "trickle renomination";
    private final static String HARDCODED_CONNECTION = "IN IP4 0.0.0.0";

    public final int version;
    public final String name;
    public final String connectionData;
    public final ArrayListMultimap<String, String> attributes;
    public final List<Media> media;


    public SessionDescription(int version, String name, String connectionData, ArrayListMultimap<String, String> attributes, List<Media> media) {
        this.version = version;
        this.name = name;
        this.connectionData = connectionData;
        this.attributes = attributes;
        this.media = media;
    }

    private static void appendAttributes(StringBuilder s, ArrayListMultimap<String, String> attributes) {
        for (Map.Entry<String, String> attribute : attributes.entries()) {
            final String key = attribute.getKey();
            final String value = attribute.getValue();
            s.append("a=").append(key);
            if (!Strings.isNullOrEmpty(value)) {
                s.append(':').append(value);
            }
            s.append(LINE_DIVIDER);
        }
    }

    public static SessionDescription parse(final Map<String, RtpContentMap.DescriptionTransport> contents) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        return sessionDescriptionBuilder.createSessionDescription();
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
                case 'v':
                    sessionDescriptionBuilder.setVersion(ignorantIntParser(value));
                    break;
                case 'c':
                    if (currentMediaBuilder != null) {
                        currentMediaBuilder.setConnectionData(value);
                    } else {
                        sessionDescriptionBuilder.setConnectionData(value);
                    }
                    break;
                case 's':
                    sessionDescriptionBuilder.setName(value);
                    break;
                case 'a':
                    final Pair<String, String> attribute = parseAttribute(value);
                    attributeMap.put(attribute.first, attribute.second);
                    break;
                case 'm':
                    if (currentMediaBuilder == null) {
                        sessionDescriptionBuilder.setAttributes(attributeMap);
                        ;
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
                    break;
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

    public static SessionDescription of(final RtpContentMap contentMap) {
        final SessionDescriptionBuilder sessionDescriptionBuilder = new SessionDescriptionBuilder();
        final ArrayListMultimap<String, String> attributeMap = ArrayListMultimap.create();
        final ImmutableList.Builder<Media> mediaListBuilder = new ImmutableList.Builder<>();
        final Group group = contentMap.group;
        if (group != null) {
            attributeMap.put("group", group.getSemantics() + " " + Joiner.on(' ').join(group.getIdentificationTags()));
        }

        attributeMap.put("msid-semantic", " WMS my-media-stream");

        for (Map.Entry<String, RtpContentMap.DescriptionTransport> entry : contentMap.contents.entrySet()) {
            final String name = entry.getKey();
            RtpContentMap.DescriptionTransport descriptionTransport = entry.getValue();
            RtpDescription description = descriptionTransport.description;
            IceUdpTransportInfo transport = descriptionTransport.transport;
            final ArrayListMultimap<String, String> mediaAttributes = ArrayListMultimap.create();
            final String ufrag = transport.getAttribute("ufrag");
            final String pwd = transport.getAttribute("pwd");
            if (!Strings.isNullOrEmpty(ufrag)) {
                mediaAttributes.put("ice-ufrag", ufrag);
            }
            if (!Strings.isNullOrEmpty(pwd)) {
                mediaAttributes.put("ice-pwd", pwd);
            }
            mediaAttributes.put("ice-options", HARDCODED_ICE_OPTIONS);
            final IceUdpTransportInfo.Fingerprint fingerprint = transport.getFingerprint();
            if (fingerprint != null) {
                mediaAttributes.put("fingerprint", fingerprint.getHash() + " " + fingerprint.getContent());
                mediaAttributes.put("setup", fingerprint.getSetup());
            }
            final ImmutableList.Builder<Integer> formatBuilder = new ImmutableList.Builder<>();
            for (RtpDescription.PayloadType payloadType : description.getPayloadTypes()) {
                formatBuilder.add(payloadType.getIntId());
                mediaAttributes.put("rtpmap", payloadType.toSdpAttribute());
                List<RtpDescription.Parameter> parameters = payloadType.getParameters();
                if (parameters.size() > 0) {
                    mediaAttributes.put("fmtp", RtpDescription.Parameter.toSdpString(payloadType.getId(), parameters));
                }
                for (RtpDescription.FeedbackNegotiation feedbackNegotiation : payloadType.getFeedbackNegotiations()) {
                    mediaAttributes.put("rtcp-fb", payloadType.getId() + " " + feedbackNegotiation.getType() + (Strings.isNullOrEmpty(feedbackNegotiation.getSubType()) ? "" : " " + feedbackNegotiation.getSubType()));
                }
                for (RtpDescription.FeedbackNegotiationTrrInt feedbackNegotiationTrrInt : payloadType.feedbackNegotiationTrrInts()) {
                    mediaAttributes.put("rtcp-fb", payloadType.getId() + " trr-int " + feedbackNegotiationTrrInt.getValue());
                }
            }

            for (RtpDescription.FeedbackNegotiation feedbackNegotiation : description.getFeedbackNegotiations()) {
                mediaAttributes.put("rtcp-fb", "* " + feedbackNegotiation.getType() + (Strings.isNullOrEmpty(feedbackNegotiation.getSubType()) ? "" : " " + feedbackNegotiation.getSubType()));
            }
            for (RtpDescription.FeedbackNegotiationTrrInt feedbackNegotiationTrrInt : description.feedbackNegotiationTrrInts()) {
                mediaAttributes.put("rtcp-fb", "* trr-int " + feedbackNegotiationTrrInt.getValue());
            }
            for (RtpDescription.RtpHeaderExtension extension : description.getHeaderExtensions()) {
                mediaAttributes.put("extmap", extension.getId() + " " + extension.getUri());
            }
            for (RtpDescription.Source source : description.getSources()) {
                for (RtpDescription.Source.Parameter parameter : source.getParameters()) {
                    mediaAttributes.put("ssrc", source.getSsrcId() + " " + parameter.getParameterName() + ":" + parameter.getParameterValue());
                }
            }

            mediaAttributes.put("mid", name);

            //random additional attributes
            mediaAttributes.put("rtcp", "9 IN IP4 0.0.0.0");
            mediaAttributes.put("sendrecv", "");
            mediaAttributes.put("rtcp-mux", "");

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

    public static int ignorantIntParser(final String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 0;
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

    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder()
                .append("v=").append(version).append(LINE_DIVIDER)
                .append("o=- 8770656990916039506 2 IN IP4 127.0.0.1").append(LINE_DIVIDER) //what ever that means
                .append("s=").append(name).append(LINE_DIVIDER)
                .append("t=0 0").append(LINE_DIVIDER);
        appendAttributes(s, attributes);
        for (Media media : this.media) {
            s.append("m=").append(media.media).append(' ').append(media.port).append(' ').append(media.protocol).append(' ').append(Joiner.on(' ').join(media.formats)).append(LINE_DIVIDER);
            s.append("c=").append(media.connectionData).append(LINE_DIVIDER);
            appendAttributes(s, media.attributes);
        }
        return s.toString();
    }

    public static class Media {
        public final String media;
        public final int port;
        public final String protocol;
        public final List<Integer> formats;
        public final String connectionData;
        public final ArrayListMultimap<String, String> attributes;

        public Media(String media, int port, String protocol, List<Integer> formats, String connectionData, ArrayListMultimap<String, String> attributes) {
            this.media = media;
            this.port = port;
            this.protocol = protocol;
            this.formats = formats;
            this.connectionData = connectionData;
            this.attributes = attributes;
        }
    }

}
