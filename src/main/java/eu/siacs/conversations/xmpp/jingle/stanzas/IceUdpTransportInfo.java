package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.SessionDescription;

public class IceUdpTransportInfo extends GenericTransportInfo {

    private IceUdpTransportInfo() {
        super("transport", Namespace.JINGLE_TRANSPORT_ICE_UDP);
    }

    public Fingerprint getFingerprint() {
        final Element fingerprint = this.findChild("fingerprint", Namespace.JINGLE_APPS_DTLS);
        return fingerprint == null ? null : Fingerprint.upgrade(fingerprint);
    }

    public List<Candidate> getCandidates() {
        final ImmutableList.Builder<Candidate> builder = new ImmutableList.Builder<>();
        for (final Element child : getChildren()) {
            if ("candidate".equals(child.getName())) {
                builder.add(Candidate.upgrade(child));
            }
        }
        return builder.build();
    }

    public static IceUdpTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument("transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(Namespace.JINGLE_TRANSPORT_ICE_UDP.equals(element.getNamespace()), "Element does not match ice-udp transport namespace");
        final IceUdpTransportInfo transportInfo = new IceUdpTransportInfo();
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }

    public static IceUdpTransportInfo of(SessionDescription sessionDescription, SessionDescription.Media media) {
        final String ufrag = Iterables.getFirst(media.attributes.get("ice-ufrag"), null);
        final String pwd = Iterables.getFirst(media.attributes.get("ice-pwd"), null);
        IceUdpTransportInfo iceUdpTransportInfo = new IceUdpTransportInfo();
        if (ufrag != null) {
            iceUdpTransportInfo.setAttribute("ufrag", ufrag);
        }
        if (pwd != null) {
            iceUdpTransportInfo.setAttribute("pwd", pwd);
        }
        final Fingerprint fingerprint = Fingerprint.of(sessionDescription, media);
        if (fingerprint != null) {
            iceUdpTransportInfo.addChild(fingerprint);
        }
        return iceUdpTransportInfo;

    }

    public static class Candidate extends Element {

        private Candidate() {
            super("candidate");
        }

        public int getComponent() {
            return getAttributeAsInt("component");
        }

        public int getFoundation() {
            return getAttributeAsInt("foundation");
        }

        public int getGeneration() {
            return getAttributeAsInt("generation");
        }

        public String getId() {
            return getAttribute("id");
        }

        public String getIp() {
            return getAttribute("ip");
        }

        public int getNetwork() {
            return getAttributeAsInt("network");
        }

        public int getPort() {
            return getAttributeAsInt("port");
        }

        public int getPriority() {
            return getAttributeAsInt("priority");
        }

        public String getProtocol() {
            return getAttribute("protocol");
        }

        public String getRelAddr() {
            return getAttribute("rel-addr");
        }

        public int getRelPort() {
            return getAttributeAsInt("rel-port");
        }

        public String getType() { //TODO might be converted to enum
            return getAttribute("type");
        }

        private int getAttributeAsInt(final String name) {
            final String value = this.getAttribute(name);
            if (value == null) {
                return 0;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        public static Candidate upgrade(final Element element) {
            Preconditions.checkArgument("candidate".equals(element.getName()));
            final Candidate candidate = new Candidate();
            candidate.setAttributes(element.getAttributes());
            candidate.setChildren(element.getChildren());
            return candidate;
        }
    }


    public static class Fingerprint extends Element {

        public String getHash() {
            return this.getAttribute("hash");
        }

        public String getSetup() {
            return this.getAttribute("setup");
        }

        private Fingerprint() {
            super("fingerprint", Namespace.JINGLE_APPS_DTLS);
        }

        public static Fingerprint upgrade(final Element element) {
            Preconditions.checkArgument("fingerprint".equals(element.getName()));
            Preconditions.checkArgument(Namespace.JINGLE_APPS_DTLS.equals(element.getNamespace()));
            final Fingerprint fingerprint = new Fingerprint();
            fingerprint.setAttributes(element.getAttributes());
            fingerprint.setContent(element.getContent());
            return fingerprint;
        }

        private static Fingerprint of(ArrayListMultimap<String, String> attributes) {
            final String fingerprint = Iterables.getFirst(attributes.get("fingerprint"), null);
            final String setup = Iterables.getFirst(attributes.get("setup"), null);
            if (setup != null && fingerprint != null) {
                final String[] fingerprintParts = fingerprint.split(" ", 2);
                if (fingerprintParts.length == 2) {
                    final String hash = fingerprintParts[0];
                    final String actualFingerprint = fingerprintParts[1];
                    final Fingerprint element = new Fingerprint();
                    element.setAttribute("hash", hash);
                    element.setAttribute("setup", setup);
                    element.setContent(actualFingerprint);
                    return element;
                }
            }
            return null;
        }

        public static Fingerprint of(final SessionDescription sessionDescription, final SessionDescription.Media media) {
            final Fingerprint fingerprint = of(media.attributes);
            return fingerprint == null ? of(sessionDescription.attributes) : fingerprint;
        }
    }
}
