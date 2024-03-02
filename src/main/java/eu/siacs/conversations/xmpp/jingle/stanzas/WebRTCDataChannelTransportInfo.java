package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;

import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.SessionDescription;
import eu.siacs.conversations.xmpp.jingle.transports.Transport;

public class WebRTCDataChannelTransportInfo extends GenericTransportInfo {

    public static final WebRTCDataChannelTransportInfo STUB = new WebRTCDataChannelTransportInfo();

    public WebRTCDataChannelTransportInfo() {
        super("transport", Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL);
    }

    public static WebRTCDataChannelTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument(
                "transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(
                Namespace.JINGLE_TRANSPORT_WEBRTC_DATA_CHANNEL.equals(element.getNamespace()),
                "Element does not match ice-udp transport namespace");
        final WebRTCDataChannelTransportInfo transportInfo = new WebRTCDataChannelTransportInfo();
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }

    public IceUdpTransportInfo innerIceUdpTransportInfo() {
        final var iceUdpTransportInfo =
                this.findChild("transport", Namespace.JINGLE_TRANSPORT_ICE_UDP);
        if (iceUdpTransportInfo != null) {
            return IceUdpTransportInfo.upgrade(iceUdpTransportInfo);
        }
        return null;
    }

    public static Transport.InitialTransportInfo of(final SessionDescription sessionDescription) {
        final SessionDescription.Media media = Iterables.getOnlyElement(sessionDescription.media);
        final String id = Iterables.getFirst(media.attributes.get("mid"), null);
        Preconditions.checkNotNull(id, "media has no mid");
        final String maxMessageSize =
                Iterables.getFirst(media.attributes.get("max-message-size"), null);
        final Integer maxMessageSizeInt =
                maxMessageSize == null ? null : Ints.tryParse(maxMessageSize);
        final String sctpPort = Iterables.getFirst(media.attributes.get("sctp-port"), null);
        final Integer sctpPortInt = sctpPort == null ? null : Ints.tryParse(sctpPort);
        final WebRTCDataChannelTransportInfo webRTCDataChannelTransportInfo =
                new WebRTCDataChannelTransportInfo();
        if (maxMessageSizeInt != null) {
            webRTCDataChannelTransportInfo.setAttribute("max-message-size", maxMessageSizeInt);
        }
        if (sctpPortInt != null) {
            webRTCDataChannelTransportInfo.setAttribute("sctp-port", sctpPortInt);
        }
        webRTCDataChannelTransportInfo.addChild(IceUdpTransportInfo.of(sessionDescription, media));

        final String groupAttribute =
                Iterables.getFirst(sessionDescription.attributes.get("group"), null);
        final Group group = groupAttribute == null ? null : Group.ofSdpString(groupAttribute);
        return new Transport.InitialTransportInfo(id, webRTCDataChannelTransportInfo, group);
    }

    public Integer getSctpPort() {
        final var attribute = this.getAttribute("sctp-port");
        if (attribute == null) {
            return null;
        }
        return Ints.tryParse(attribute);
    }

    public Integer getMaxMessageSize() {
        final var attribute = this.getAttribute("max-message-size");
        if (attribute == null) {
            return null;
        }
        return Ints.tryParse(attribute);
    }

    public WebRTCDataChannelTransportInfo cloneWrapper() {
        final var iceUdpTransport = this.innerIceUdpTransportInfo();
        final WebRTCDataChannelTransportInfo transportInfo = new WebRTCDataChannelTransportInfo();
        transportInfo.setAttributes(new Hashtable<>(getAttributes()));
        transportInfo.addChild(iceUdpTransport.cloneWrapper());
        return transportInfo;
    }

    public void addCandidate(final IceUdpTransportInfo.Candidate candidate) {
        this.innerIceUdpTransportInfo().addChild(candidate);
    }

    public List<IceUdpTransportInfo.Candidate> getCandidates() {
        final var innerTransportInfo = this.innerIceUdpTransportInfo();
        if (innerTransportInfo == null) {
            return Collections.emptyList();
        }
        return innerTransportInfo.getCandidates();
    }

    public IceUdpTransportInfo.Credentials getCredentials() {
        final var innerTransportInfo = this.innerIceUdpTransportInfo();
        return innerTransportInfo == null ? null : innerTransportInfo.getCredentials();
    }
}
