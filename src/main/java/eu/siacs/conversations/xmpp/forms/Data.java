package eu.siacs.conversations.xmpp.forms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.xml.Element;

public class Data extends Element {

	public Data() {
		super("x");
		this.setAttribute("xmlns","jabber:x:data");
	}

	public List<Field> getFields() {
		ArrayList<Field> fields = new ArrayList<Field>();
		for(Element child : getChildren()) {
			if (child.getName().equals("field")) {
				fields.add(Field.parse(child));
			}
		}
		return fields;
	}

	public Field getFieldByName(String needle) {
		for(Element child : getChildren()) {
			if (child.getName().equals("field") && needle.equals(child.getAttribute("var"))) {
				return Field.parse(child);
			}
		}
		return null;
	}

	public void put(String name, String value) {
		Field field = getFieldByName(name);
		if (field == null) {
			field = new Field(name);
			this.addChild(field);
		}
		field.setValue(value);
	}

	public void put(String name, Collection<String> values) {
		Field field = getFieldByName(name);
		if (field == null) {
			field = new Field(name);
			this.addChild(field);
		}
		field.setValues(values);
	}

	public void submit() {
		this.setAttribute("type","submit");
		removeNonFieldChildren();
		for(Field field : getFields()) {
			field.removeNonValueChildren();
		}
	}

	private void removeNonFieldChildren() {
		for(Iterator<Element> iterator = this.children.iterator(); iterator.hasNext();) {
			Element element = iterator.next();
			if (!element.getName().equals("field")) {
				iterator.remove();
			}
		}
	}

	public static Data parse(Element element) {
		Data data = new Data();
		data.setAttributes(element.getAttributes());
		data.setChildren(element.getChildren());
		return data;
	}

	public void setFormType(String formType) {
		this.put("FORM_TYPE",formType);
	}

	public String getFormType() {
		return this.getAttribute("FORM_TYPE");
	}
}
