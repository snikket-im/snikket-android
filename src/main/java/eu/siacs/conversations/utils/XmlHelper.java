package eu.siacs.conversations.utils;

import eu.siacs.conversations.xml.Element;

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
		final StringBuilder builder = new StringBuilder();
		builder.append('[');
		if (element != null) {
			for (Element child : element.getChildren()) {
				if (builder.length() != 1) {
					builder.append(',');
				}
				builder.append(child.getName());
			}
		}
		builder.append(']');
		return builder.toString();
	}
}
