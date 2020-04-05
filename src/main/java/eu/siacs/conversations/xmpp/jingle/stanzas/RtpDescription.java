package eu.siacs.conversations.xmpp.jingle.stanzas;

import android.util.Pair;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.SessionDescription;

public class RtpDescription extends GenericDescription {


    private RtpDescription(final String media) {
        super("description", Namespace.JINGLE_APPS_RTP);
        this.setAttribute("media", media);
    }

    private RtpDescription() {
        super("description", Namespace.JINGLE_APPS_RTP);
    }

    public Media getMedia() {
        return Media.of(this.getAttribute("media"));
    }

    public List<PayloadType> getPayloadTypes() {
        final ImmutableList.Builder<PayloadType> builder = new ImmutableList.Builder<>();
        for (Element child : getChildren()) {
            if ("payload-type".equals(child.getName())) {
                builder.add(PayloadType.of(child));
            }
        }
        return builder.build();
    }

    public List<FeedbackNegotiation> getFeedbackNegotiations() {
        return FeedbackNegotiation.fromChildren(this.getChildren());
    }

    public List<FeedbackNegotiationTrrInt> feedbackNegotiationTrrInts() {
        return FeedbackNegotiationTrrInt.fromChildren(this.getChildren());
    }

    public List<RtpHeaderExtension> getHeaderExtensions() {
        final ImmutableList.Builder<RtpHeaderExtension> builder = new ImmutableList.Builder<>();
        for (final Element child : getChildren()) {
            if ("rtp-hdrext".equals(child.getName()) && Namespace.JINGLE_RTP_HEADER_EXTENSIONS.equals(child.getNamespace())) {
                builder.add(RtpHeaderExtension.upgrade(child));
            }
        }
        return builder.build();
    }

    public List<Source> getSources() {
        final ImmutableList.Builder<Source> builder = new ImmutableList.Builder<>();
        for (final Element child : this.children) {
            if ("source".equals(child.getName()) && Namespace.JINGLE_RTP_SOURCE_SPECIFIC_MEDIA_ATTRIBUTES.equals(child.getNamespace())) {
                builder.add(Source.upgrade(child));
            }
        }
        return builder.build();
    }

    public static RtpDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()), "Name of provided element is not description");
        Preconditions.checkArgument(Namespace.JINGLE_APPS_RTP.equals(element.getNamespace()), "Element does not match the jingle rtp namespace");
        final RtpDescription description = new RtpDescription();
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }

    public static class FeedbackNegotiation extends Element {
        private FeedbackNegotiation() {
            super("rtcp-fb", Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION);
        }

        public FeedbackNegotiation(String type, String subType) {
            super("rtcp-fb", Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION);
            this.setAttribute("type", type);
            if (subType != null) {
                this.setAttribute("subtype", subType);
            }
        }

        public String getType() {
            return this.getAttribute("type");
        }

        public String getSubType() {
            return this.getAttribute("subtype");
        }

        private static FeedbackNegotiation upgrade(final Element element) {
            Preconditions.checkArgument("rtcp-fb".equals(element.getName()));
            Preconditions.checkArgument(Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION.equals(element.getNamespace()));
            final FeedbackNegotiation feedback = new FeedbackNegotiation();
            feedback.setAttributes(element.getAttributes());
            feedback.setChildren(element.getChildren());
            return feedback;
        }

        public static List<FeedbackNegotiation> fromChildren(final List<Element> children) {
            ImmutableList.Builder<FeedbackNegotiation> builder = new ImmutableList.Builder<>();
            for (final Element child : children) {
                if ("rtcp-fb".equals(child.getName()) && Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION.equals(child.getNamespace())) {
                    builder.add(upgrade(child));
                }
            }
            return builder.build();
        }

    }

    public static class FeedbackNegotiationTrrInt extends Element {

        private FeedbackNegotiationTrrInt(int value) {
            super("rtcp-fb-trr-int", Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION);
            this.setAttribute("value", value);
        }


        private FeedbackNegotiationTrrInt() {
            super("rtcp-fb-trr-int", Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION);
        }

        public int getValue() {
            final String value = getAttribute("value");
            if (value == null) {
                return 0;
            }
            return SessionDescription.ignorantIntParser(value);

        }

        private static FeedbackNegotiationTrrInt upgrade(final Element element) {
            Preconditions.checkArgument("rtcp-fb-trr-int".equals(element.getName()));
            Preconditions.checkArgument(Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION.equals(element.getNamespace()));
            final FeedbackNegotiationTrrInt trr = new FeedbackNegotiationTrrInt();
            trr.setAttributes(element.getAttributes());
            trr.setChildren(element.getChildren());
            return trr;
        }

        public static List<FeedbackNegotiationTrrInt> fromChildren(final List<Element> children) {
            ImmutableList.Builder<FeedbackNegotiationTrrInt> builder = new ImmutableList.Builder<>();
            for (final Element child : children) {
                if ("rtcp-fb-trr-int".equals(child.getName()) && Namespace.JINGLE_RTP_FEEDBACK_NEGOTIATION.equals(child.getNamespace())) {
                    builder.add(upgrade(child));
                }
            }
            return builder.build();
        }
    }


    //XEP-0294: Jingle RTP Header Extensions Negotiation
    //maps to `extmap:$id $uri`
    public static class RtpHeaderExtension extends Element {

        private RtpHeaderExtension() {
            super("rtp-hdrext", Namespace.JINGLE_RTP_HEADER_EXTENSIONS);
        }

        public RtpHeaderExtension(String id, String uri) {
            super("rtp-hdrext", Namespace.JINGLE_RTP_HEADER_EXTENSIONS);
            this.setAttribute("id", id);
            this.setAttribute("uri", uri);
        }

        public String getId() {
            return this.getAttribute("id");
        }

        public String getUri() {
            return this.getAttribute("uri");
        }

        public static RtpHeaderExtension upgrade(final Element element) {
            Preconditions.checkArgument("rtp-hdrext".equals(element.getName()));
            Preconditions.checkArgument(Namespace.JINGLE_RTP_HEADER_EXTENSIONS.equals(element.getNamespace()));
            final RtpHeaderExtension extension = new RtpHeaderExtension();
            extension.setAttributes(element.getAttributes());
            extension.setChildren(element.getChildren());
            return extension;
        }

        public static RtpHeaderExtension ofSdpString(final String sdp) {
            final String[] pair = sdp.split(" ", 2);
            if (pair.length == 2) {
                final String id = pair[0];
                final String uri = pair[1];
                return new RtpHeaderExtension(id, uri);
            } else {
                return null;
            }
        }
    }

    //maps to `rtpmap:$id $name/$clockrate/$channels`
    public static class PayloadType extends Element {

        private PayloadType() {
            super("payload-type", Namespace.JINGLE_APPS_RTP);
        }

        public PayloadType(String id, String name, int clockRate, int channels) {
            super("payload-type", Namespace.JINGLE_APPS_RTP);
            this.setAttribute("id", id);
            this.setAttribute("name", name);
            this.setAttribute("clockrate", clockRate);
            if (channels != 1) {
                this.setAttribute("channels", channels);
            }
        }

        public String toSdpAttribute() {
            final int channels = getChannels();
            return getId()+" "+getPayloadTypeName()+"/"+getClockRate()+(channels == 1 ? "" : "/"+channels);
        }

        public int getIntId() {
            final String id = this.getAttribute("id");
            return id == null ? 0 : SessionDescription.ignorantIntParser(id);
        }

        public String getId() {
            return this.getAttribute("id");
        }


        public String getPayloadTypeName() {
            return this.getAttribute("name");
        }

        public int getClockRate() {
            final String clockRate = this.getAttribute("clockrate");
            if (clockRate == null) {
                return 0;
            }
            try {
                return Integer.parseInt(clockRate);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public int getChannels() {
            final String channels = this.getAttribute("channels");
            if (channels == null) {
                return 1; // The number of channels; if omitted, it MUST be assumed to contain one channel
            }
            try {
                return Integer.parseInt(channels);
            } catch (NumberFormatException e) {
                return 1;
            }
        }

        public List<Parameter> getParameters() {
            final ImmutableList.Builder<Parameter> builder = new ImmutableList.Builder<>();
            for (Element child : getChildren()) {
                if ("parameter".equals(child.getName())) {
                    builder.add(Parameter.of(child));
                }
            }
            return builder.build();
        }

        public List<FeedbackNegotiation> getFeedbackNegotiations() {
            return FeedbackNegotiation.fromChildren(this.getChildren());
        }

        public List<FeedbackNegotiationTrrInt> feedbackNegotiationTrrInts() {
            return FeedbackNegotiationTrrInt.fromChildren(this.getChildren());
        }

        public static PayloadType of(final Element element) {
            Preconditions.checkArgument("payload-type".equals(element.getName()), "element name must be called payload-type");
            PayloadType payloadType = new PayloadType();
            payloadType.setAttributes(element.getAttributes());
            payloadType.setChildren(element.getChildren());
            return payloadType;
        }

        public static PayloadType ofSdpString(final String sdp) {
            final String[] pair = sdp.split(" ", 2);
            if (pair.length == 2) {
                final String id = pair[0];
                final String[] parts = pair[1].split("/");
                if (parts.length >= 2) {
                    final String name = parts[0];
                    final int clockRate = SessionDescription.ignorantIntParser(parts[1]);
                    final int channels;
                    if (parts.length >= 3) {
                        channels = SessionDescription.ignorantIntParser(parts[2]);
                    } else {
                        channels = 1;
                    }
                    return new PayloadType(id, name, clockRate, channels);
                }
            }
            return null;
        }

        public void addChildren(final List<Element> children) {
            if (children != null) {
                this.children.addAll(children);
            }
        }

        public void addParameters(List<Parameter> parameters) {
            if (parameters != null) {
                this.children.addAll(parameters);
            }
        }
    }

    //map to `fmtp $id key=value;key=value
    //where id is the id of the parent payload-type
    public static class Parameter extends Element {

        private Parameter() {
            super("parameter", Namespace.JINGLE_APPS_RTP);
        }

        public Parameter(String name, String value) {
            super("parameter", Namespace.JINGLE_APPS_RTP);
            this.setAttribute("name", name);
            this.setAttribute("value", value);
        }

        public String getParameterName() {
            return this.getAttribute("name");
        }

        public String getParameterValue() {
            return this.getAttribute("value");
        }

        public static Parameter of(final Element element) {
            Preconditions.checkArgument("parameter".equals(element.getName()), "element name must be called parameter");
            Parameter parameter = new Parameter();
            parameter.setAttributes(element.getAttributes());
            parameter.setChildren(element.getChildren());
            return parameter;
        }

        public static String toSdpString(final String id, List<Parameter> parameters) {
            final StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(id).append(' ');
            for(int i = 0; i < parameters.size(); ++i) {
                Parameter p = parameters.get(i);
                stringBuilder.append(p.getParameterName()).append('=').append(p.getParameterValue());
                if (i != parameters.size() - 1) {
                    stringBuilder.append(';');
                }
            }
            return stringBuilder.toString();
        }

        public static Pair<String, List<Parameter>> ofSdpString(final String sdp) {
            final String[] pair = sdp.split(" ");
            if (pair.length == 2) {
                final String id = pair[0];
                ImmutableList.Builder<Parameter> builder = new ImmutableList.Builder<>();
                for (final String parameter : pair[1].split(";")) {
                    final String[] parts = parameter.split("=", 2);
                    if (parts.length == 2) {
                        builder.add(new Parameter(parts[0], parts[1]));
                    }
                }
                return new Pair<>(id, builder.build());
            } else {
                return null;
            }
        }
    }

    //XEP-0339: Source-Specific Media Attributes in Jingle
    //maps to `a=ssrc:<ssrc-id> <attribute>:<value>`
    public static class Source extends Element {

        private Source() {
            super("source", Namespace.JINGLE_RTP_SOURCE_SPECIFIC_MEDIA_ATTRIBUTES);
        }

        public Source(String ssrcId, Collection<Parameter> parameters) {
            super("source", Namespace.JINGLE_RTP_SOURCE_SPECIFIC_MEDIA_ATTRIBUTES);
            this.setAttribute("ssrc", ssrcId);
            for (Parameter parameter : parameters) {
                this.addChild(parameter);
            }
        }

        public String getSsrcId() {
            return this.getAttribute("ssrc");
        }

        public List<Parameter> getParameters() {
            ImmutableList.Builder<Parameter> builder = new ImmutableList.Builder<>();
            for (Element child : this.children) {
                if ("parameter".equals(child.getName())) {
                    builder.add(Parameter.upgrade(child));
                }
            }
            return builder.build();
        }

        public static Source upgrade(final Element element) {
            Preconditions.checkArgument("source".equals(element.getName()));
            Preconditions.checkArgument(Namespace.JINGLE_RTP_SOURCE_SPECIFIC_MEDIA_ATTRIBUTES.equals(element.getNamespace()));
            final Source source = new Source();
            source.setChildren(element.getChildren());
            source.setAttributes(element.getAttributes());
            return source;
        }

        public static class Parameter extends Element {

            public String getParameterName() {
                return this.getAttribute("name");
            }

            public String getParameterValue() {
                return this.getAttribute("value");
            }

            private Parameter() {
                super("parameter");
            }

            public Parameter(final String attribute, final String value) {
                super("parameter");
                this.setAttribute("name", attribute);
                if (value != null) {
                    this.setAttribute("value", value);
                }
            }

            public static Parameter upgrade(final Element element) {
                Preconditions.checkArgument("parameter".equals(element.getName()));
                Parameter parameter = new Parameter();
                parameter.setAttributes(element.getAttributes());
                parameter.setChildren(element.getChildren());
                return parameter;
            }
        }

    }

    public enum Media {
        VIDEO, AUDIO, UNKNOWN;

        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ROOT);
        }

        public static Media of(String value) {
            try {
                return value == null ? UNKNOWN : Media.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }

    public static RtpDescription of(final SessionDescription.Media media) {
        final RtpDescription rtpDescription = new RtpDescription(media.media);
        final Map<String, List<Parameter>> parameterMap = new HashMap<>();
        final ArrayListMultimap<String, Element> feedbackNegotiationMap = ArrayListMultimap.create();
        final ArrayListMultimap<String, Source.Parameter> sourceParameterMap = ArrayListMultimap.create();
        for (final String rtcpFb : media.attributes.get("rtcp-fb")) {
            final String[] parts = rtcpFb.split(" ");
            if (parts.length >= 2) {
                final String id = parts[0];
                final String type = parts[1];
                final String subType = parts.length >= 3 ? parts[2] : null;
                if ("trr-int".equals(type)) {
                    if (subType != null) {
                        feedbackNegotiationMap.put(id, new FeedbackNegotiationTrrInt(SessionDescription.ignorantIntParser(subType)));
                    }
                } else {
                    feedbackNegotiationMap.put(id, new FeedbackNegotiation(type, subType));
                }
            }
        }
        for (final String ssrc : media.attributes.get(("ssrc"))) {
            final String[] parts = ssrc.split(" ", 2);
            if (parts.length == 2) {
                final String id = parts[0];
                final String[] subParts = parts[1].split(":", 2);
                final String attribute = subParts[0];
                final String value = subParts.length == 2 ? subParts[1] : null;
                sourceParameterMap.put(id, new Source.Parameter(attribute, value));
            }
        }
        for (final String fmtp : media.attributes.get("fmtp")) {
            final Pair<String, List<Parameter>> pair = Parameter.ofSdpString(fmtp);
            if (pair != null) {
                parameterMap.put(pair.first, pair.second);
            }
        }
        rtpDescription.addChildren(feedbackNegotiationMap.get("*"));
        for (final String rtpmap : media.attributes.get("rtpmap")) {
            final PayloadType payloadType = PayloadType.ofSdpString(rtpmap);
            if (payloadType != null) {
                payloadType.addParameters(parameterMap.get(payloadType.getId()));
                payloadType.addChildren(feedbackNegotiationMap.get(payloadType.getId()));
                rtpDescription.addChild(payloadType);
            }
        }
        for (final String extmap : media.attributes.get("extmap")) {
            final RtpHeaderExtension extension = RtpHeaderExtension.ofSdpString(extmap);
            if (extension != null) {
                rtpDescription.addChild(extension);
            }
        }
        for (Map.Entry<String, Collection<Source.Parameter>> source : sourceParameterMap.asMap().entrySet()) {
            rtpDescription.addChild(new Source(source.getKey(), source.getValue()));
        }
        return rtpDescription;
    }

    private void addChildren(List<Element> elements) {
        if (elements != null) {
            this.children.addAll(elements);
        }
    }
}
