package eu.siacs.conversations.xml;

import androidx.annotation.NonNull;
import com.google.common.base.CaseFormat;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import eu.siacs.conversations.utils.XmlHelper;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.xmpp.model.stanza.Message;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Element {
    private final String name;
    private Hashtable<String, String> attributes = new Hashtable<>();
    private String content;
    protected List<Element> children = new ArrayList<>();

    public Element(String name) {
        this.name = name;
    }

    public Element(String name, String xmlns) {
        this.name = name;
        this.setAttribute("xmlns", xmlns);
    }

    public Element addChild(Element child) {
        this.content = null;
        children.add(child);
        return child;
    }

    public Element addChild(String name) {
        this.content = null;
        Element child = new Element(name);
        children.add(child);
        return child;
    }

    public Element addChild(String name, String xmlns) {
        this.content = null;
        Element child = new Element(name);
        child.setAttribute("xmlns", xmlns);
        children.add(child);
        return child;
    }

    public Element setContent(String content) {
        this.content = content;
        this.children.clear();
        return this;
    }

    public Element findChild(String name) {
        for (Element child : this.children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public String findChildContent(String name) {
        Element element = findChild(name);
        return element == null ? null : element.getContent();
    }

    public Element findChild(String name, String xmlns) {
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
                return child;
            }
        }
        return null;
    }

    public Element findChildEnsureSingle(String name, String xmlns) {
        final List<Element> results = new ArrayList<>();
        for (Element child : this.children) {
            if (name.equals(child.getName()) && xmlns.equals(child.getAttribute("xmlns"))) {
                results.add(child);
            }
        }
        if (results.size() == 1) {
            return results.get(0);
        }
        return null;
    }

    public String findChildContent(String name, String xmlns) {
        Element element = findChild(name, xmlns);
        return element == null ? null : element.getContent();
    }

    public boolean hasChild(final String name) {
        return findChild(name) != null;
    }

    public boolean hasChild(final String name, final String xmlns) {
        return findChild(name, xmlns) != null;
    }

    public List<Element> getChildren() {
        return this.children;
    }

    public Element setChildren(List<Element> children) {
        this.children = children;
        return this;
    }

    public final String getContent() {
        return content;
    }

    public Element setAttribute(String name, String value) {
        if (name != null && value != null) {
            this.attributes.put(name, value);
        }
        return this;
    }

    public Element setAttribute(final String name, final Enum<?> e) {
        if (e == null) {
            this.attributes.remove(name);
        } else {
            this.attributes.put(
                    name, CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, e.toString()));
        }
        return this;
    }

    public Element setAttribute(String name, Jid value) {
        if (name != null && value != null) {
            this.attributes.put(name, value.toString());
        }
        return this;
    }

    public void setAttribute(final String name, final boolean value) {
        this.setAttribute(name, value ? "1" : "0");
    }

    public void removeAttribute(final String name) {
        this.attributes.remove(name);
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

    public long getLongAttribute(final String name) {
        final var value = Longs.tryParse(Strings.nullToEmpty(this.attributes.get(name)));
        return value == null ? 0 : value;
    }

    public Optional<Integer> getOptionalIntAttribute(final String name) {
        final String value = getAttribute(name);
        if (value == null) {
            return Optional.absent();
        }
        return Optional.fromNullable(Ints.tryParse(value));
    }

    public Jid getAttributeAsJid(final String name) {
        final String jid = this.getAttribute(name);
        if (Strings.isNullOrEmpty(jid)) {
            return null;
        }
        return Jid.ofOrInvalid(jid, this instanceof Message);
    }

    public Hashtable<String, String> getAttributes() {
        return this.attributes;
    }

    @NonNull
    public String toString() {
        final StringBuilder elementOutput = new StringBuilder();
        if (content == null && children.isEmpty()) {
            final Tag emptyTag = Tag.empty(name);
            emptyTag.setAttributes(this.attributes);
            elementOutput.append(emptyTag);
        } else {
            final Tag startTag = Tag.start(name);
            startTag.setAttributes(this.attributes);
            elementOutput.append(startTag);
            if (content != null) {
                elementOutput.append(XmlHelper.encodeEntities(content));
            } else {
                for (final Element child : children) {
                    elementOutput.append(child.toString());
                }
            }
            final Tag endTag = Tag.end(name);
            elementOutput.append(endTag);
        }
        return elementOutput.toString();
    }

    public final String getName() {
        return name;
    }

    public void clearChildren() {
        this.children.clear();
    }

    public void setAttribute(String name, long value) {
        this.setAttribute(name, Long.toString(value));
    }

    public void setAttribute(String name, int value) {
        this.setAttribute(name, Integer.toString(value));
    }

    public boolean getAttributeAsBoolean(String name) {
        String attr = getAttribute(name);
        return (attr != null && (attr.equalsIgnoreCase("true") || attr.equalsIgnoreCase("1")));
    }

    public String getNamespace() {
        return getAttribute("xmlns");
    }

    protected Instant getAttributeAsInstant(final String name) {
        final var value = getAttribute(name);
        if (Strings.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }
}
