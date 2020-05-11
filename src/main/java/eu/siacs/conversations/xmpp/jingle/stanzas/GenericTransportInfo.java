package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;

public class GenericTransportInfo extends Element {

    protected GenericTransportInfo(String name, String xmlns) {
        super(name, xmlns);
    }

    public static GenericTransportInfo upgrade(final Element element) {
        Preconditions.checkArgument("transport".equals(element.getName()));
        final GenericTransportInfo transport = new GenericTransportInfo("transport", element.getNamespace());
        transport.setAttributes(element.getAttributes());
        transport.setChildren(element.getChildren());
        return transport;
    }
}
