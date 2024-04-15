package eu.siacs.conversations.xml;

import androidx.annotation.NonNull;

import java.util.Hashtable;
import java.util.Map.Entry;
import java.util.Set;

import eu.siacs.conversations.utils.XmlHelper;

public class Tag {
    public static final int NO = -1;
    public static final int START = 0;
    public static final int END = 1;
    public static final int EMPTY = 2;

    protected int type;
    protected String name;
    protected Hashtable<String, String> attributes = new Hashtable<String, String>();

    protected Tag(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public static Tag no(String text) {
        return new Tag(NO, text);
    }

    public static Tag start(String name) {
        return new Tag(START, name);
    }

    public static Tag end(String name) {
        return new Tag(END, name);
    }

    public static Tag empty(String name) {
        return new Tag(EMPTY, name);
    }

    public String getName() {
        return name;
    }

    public String identifier() {
        return String.format("%s#%s", name, this.attributes.get("xmlns"));
    }

    public String getAttribute(final String attrName) {
        return this.attributes.get(attrName);
    }

    public Tag setAttribute(final String attrName, final String attrValue) {
        this.attributes.put(attrName, attrValue);
        return this;
    }

    public void setAttributes(final Hashtable<String, String> attributes) {
        this.attributes = attributes;
    }

    public boolean isStart(final String needle) {
        if (needle == null) {
            return false;
        }
        return (this.type == START) && (needle.equals(this.name));
    }

    public boolean isStart(final String name, final String namespace) {
        return isStart(name) && namespace != null && namespace.equals(this.getAttribute("xmlns"));
    }

    public boolean isEnd(String needle) {
        if (needle == null) return false;
        return (this.type == END) && (needle.equals(this.name));
    }

    public boolean isNo() {
        return (this.type == NO);
    }

    @NonNull
    public String toString() {
        final StringBuilder tagOutput = new StringBuilder();
        tagOutput.append('<');
        if (type == END) {
            tagOutput.append('/');
        }
        tagOutput.append(name);
        if (type != END) {
            final Set<Entry<String, String>> attributeSet = attributes.entrySet();
            for (final Entry<String, String> entry : attributeSet) {
                tagOutput.append(' ');
                tagOutput.append(entry.getKey());
                tagOutput.append("=\"");
                tagOutput.append(XmlHelper.encodeEntities(entry.getValue()));
                tagOutput.append('"');
            }
        }
        if (type == EMPTY) {
            tagOutput.append('/');
        }
        tagOutput.append('>');
        return tagOutput.toString();
    }

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }
}
