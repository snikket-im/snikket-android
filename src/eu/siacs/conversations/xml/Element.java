package eu.siacs.conversations.xml;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import android.util.Log;

public class Element {
	protected String name;
	protected Hashtable<String, String> attributes = new Hashtable<String, String>();
	protected String content;
	protected List<Element> children = new ArrayList<Element>();
	
	public Element(String name) {
		this.name = name;
	}
	
	public Element addChild(Element child) {
		this.content = null;
		children.add(child);
		return this;
	}
	
	public Element setContent(String content) {
		this.content = content;
		this.children.clear();
		return this;
	}
	
	public Element findChild(String name) {
		for(Element child : this.children) {
			if (child.getName().equals(name)) {
				return child;
			}
		}
		return null;
	}
	
	public boolean hasChild(String name) {
		for(Element child : this.children) {
			if (child.getName().equals(name)) {
				return true;
			}
		}
		return false;
	}
	
	public List<Element> getChildren() {
		return this.children;
	}
	
	public String getContent() {
		return content;
	}
	
	public Element setAttribute(String name, String value) {
		this.attributes.put(name, value);
		return this;
	}
	
	public Element setAttributes(Hashtable<String, String> attributes) {
		this.attributes = attributes;
		return this;
	}
	
	public String getAttribute(String name) {
		if (this.attributes.containsKey(name)) {
			return this.attributes.get(name);
		} else {
			return null;
		}
	}
	
	public String toString() {
		StringBuilder elementOutput = new StringBuilder();
		if ((content==null)&&(children.size() == 0)) {
			Tag emptyTag = Tag.empty(name);
			emptyTag.setAtttributes(this.attributes);
			elementOutput.append(emptyTag.toString());
		} else {
			Tag startTag = Tag.start(name);
			startTag.setAtttributes(this.attributes);
			elementOutput.append(startTag);
			if (content!=null) {
				elementOutput.append(content);
			} else {
				for(Element child : children) {
					elementOutput.append(child.toString());
				}
			}
			Tag endTag = Tag.end(name);
			elementOutput.append(endTag);
		}
		return elementOutput.toString();
	}

	public String getName() {
		return name;
	}
}
