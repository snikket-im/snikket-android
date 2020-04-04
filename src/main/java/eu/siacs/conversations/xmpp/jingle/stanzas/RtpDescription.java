package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class RtpDescription extends GenericDescription {


    private RtpDescription(String name, String namespace) {
        super(name, namespace);
    }

    public Media getMedia() {
        return Media.of(this.getAttribute("media"));
    }

    public List<PayloadType> getPayloadTypes() {
        final ImmutableList.Builder<PayloadType> builder = new ImmutableList.Builder<>();
        for(Element child : getChildren()) {
            if ("payload-type".equals(child.getName())) {
                builder.add(PayloadType.of(child));
            }
        }
        return builder.build();
    }

    public List<RtpHeaderExtension> getHeaderExtensions() {
        final ImmutableList.Builder<RtpHeaderExtension> builder = new ImmutableList.Builder<>();
        for(final Element child : getChildren()) {
            if ("rtp-hdrext".equals(child.getName()) && Namespace.JINGLE_RTP_HEADER_EXTENSIONS.equals(child.getNamespace())) {
                builder.add(RtpHeaderExtension.upgrade(child));
            }
        }
        return builder.build();
    }

    public static RtpDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()), "Name of provided element is not description");
        Preconditions.checkArgument(Namespace.JINGLE_APPS_RTP.equals(element.getNamespace()), "Element does not match the jingle rtp namespace");
        final RtpDescription description = new RtpDescription("description", Namespace.JINGLE_APPS_RTP);
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }

    //TODO: support for https://xmpp.org/extensions/xep-0293.html


    //XEP-0294: Jingle RTP Header Extensions Negotiation
    //maps to `extmap:$id $uri`
    public static class RtpHeaderExtension extends Element {

        private RtpHeaderExtension() {
            super("rtp-hdrext", Namespace.JINGLE_RTP_HEADER_EXTENSIONS);
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
    }

    //maps to `rtpmap $id $name/$clockrate/$channels`
    public static class PayloadType extends Element {

        private PayloadType(String name, String xmlns) {
            super(name, xmlns);
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

        public static PayloadType of(final Element element) {
            Preconditions.checkArgument("payload-type".equals(element.getName()), "element name must be called payload-type");
            PayloadType payloadType = new PayloadType("payload-type", Namespace.JINGLE_APPS_RTP);
            payloadType.setAttributes(element.getAttributes());
            payloadType.setChildren(element.getChildren());
            return payloadType;
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
}
