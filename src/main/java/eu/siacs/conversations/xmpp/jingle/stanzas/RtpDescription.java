package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;

public class RtpDescription extends GenericDescription {


    private RtpDescription(String name, String namespace) {
        super(name, namespace);
    }

    public static RtpDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()), "Name of provided element is not description");
        Preconditions.checkArgument(Namespace.JINGLE_APPS_RTP.equals(element.getNamespace()), "Element does not match the jingle rtp namespace");
        final RtpDescription description = new RtpDescription("description", Namespace.JINGLE_APPS_RTP);
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }
}
