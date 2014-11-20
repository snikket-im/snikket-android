package eu.siacs.conversations.xmpp.forms;

import java.util.Collection;
import java.util.Iterator;

import eu.siacs.conversations.xml.Element;

public class Field extends Element {

	public Field(String name) {
		super("field");
		this.setAttribute("var",name);
	}

	private Field() {
		super("field");
	}

	public String getName() {
		return this.getAttribute("var");
	}

	public void setValue(String value) {
		this.children.clear();
		this.addChild("value").setContent(value);
	}

	public void setValues(Collection<String> values) {
		this.children.clear();
		for(String value : values) {
			this.addChild("value").setContent(value);
		}
	}

	public void removeNonValueChildren() {
		for(Iterator<Element> iterator = this.children.iterator(); iterator.hasNext();) {
			Element element = iterator.next();
			if (!element.getName().equals("value")) {
				iterator.remove();
			}
		}
	}

	public static Field parse(Element element) {
		Field field = new Field();
		field.setAttributes(element.getAttributes());
		field.setChildren(element.getChildren());
		return field;
	}
}
