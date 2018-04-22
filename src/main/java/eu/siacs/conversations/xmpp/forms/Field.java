package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.xml.Element;

public class Field extends Element {

	public Field(String name) {
		super("field");
		this.setAttribute("var",name);
	}

	private Field() {
		super("field");
	}

	public String getFieldName() {
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

	public String getValue() {
		return findChildContent("value");
	}

	public List<String> getValues() {
		List<String> values = new ArrayList<>();
		for(Element child : getChildren()) {
			if ("value".equals(child.getName())) {
				values.add(child.getContent());
			}
		}
		return values;
	}

	public String getLabel() {
		return getAttribute("label");
	}

	public String getType() {
		return getAttribute("type");
	}

	public boolean isRequired() {
		return hasChild("required");
	}
}
