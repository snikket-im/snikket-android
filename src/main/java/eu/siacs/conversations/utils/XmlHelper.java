package eu.siacs.conversations.utils;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import eu.siacs.conversations.xml.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class XmlHelper {
    public static String encodeEntities(String content) {
        content = content.replace("&", "&amp;");
        content = content.replace("<", "&lt;");
        content = content.replace(">", "&gt;");
        content = content.replace("\"", "&quot;");
        content = content.replace("'", "&apos;");
        content = content.replaceAll("[\\p{Cntrl}&&[^\n\t\r]]", "");
        return content;
    }

    public static String printElementNames(final Element element) {
        final List<String> features =
                element == null
                        ? Collections.emptyList()
                        : Lists.transform(
                                element.getChildren(),
                                child -> child != null ? child.getName() : null);
        return Joiner.on(", ").join(features);
    }

    public static String print(final Collection<Element> children) {
        if (children == null) {
            return null;
        }
        return Joiner.on("").join(Iterables.transform(children, Element::toString));
    }
}
