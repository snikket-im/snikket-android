package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class IceUdpTransportInfo extends GenericTransportInfo {

    private IceUdpTransportInfo(final String name, final String xmlns) {
        super(name, xmlns);
    }

    public static IceUdpTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument("transport".equals(element.getName()), "Name of provided element is not transport");
        Preconditions.checkArgument(Namespace.JINGLE_TRANSPORT_ICE_UDP.equals(element.getNamespace()), "Element does not match ice-udp transport namespace");
        final IceUdpTransportInfo transportInfo = new IceUdpTransportInfo("transport", Namespace.JINGLE_TRANSPORT_ICE_UDP);
        transportInfo.setAttributes(element.getAttributes());
        transportInfo.setChildren(element.getChildren());
        return transportInfo;
    }
}
