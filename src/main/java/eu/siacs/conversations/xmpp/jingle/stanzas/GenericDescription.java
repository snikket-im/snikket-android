package eu.siacs.conversations.xmpp.jingle.stanzas;

import com.google.common.base.Preconditions;

import eu.siacs.conversations.xml.Element;

public class GenericDescription extends Element {

    GenericDescription(String name, final String namespace) {
        super(name, namespace);
    }

    public static GenericDescription upgrade(final Element element) {
        Preconditions.checkArgument("description".equals(element.getName()));
        final GenericDescription description = new GenericDescription("description", element.getNamespace());
        description.setAttributes(element.getAttributes());
        description.setChildren(element.getChildren());
        return description;
    }
}
