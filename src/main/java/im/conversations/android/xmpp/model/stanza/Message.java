package im.conversations.android.xmpp.model.stanza;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.jabber.Body;

import java.util.Locale;

@XmlElement
public class Message extends Stanza {

    public Message() {
        super(Message.class);
    }

    public Message(Type type) {
        this();
        this.setType(type);
    }

    public LocalizedContent getBody() {
        return findInternationalizedChildContentInDefaultNamespace("body");
    }
    
    public Type getType() {
        final var value = this.getAttribute("type");
        if (value == null) {
            return Type.NORMAL;
        } else {
            try {
                return Type.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }
    }

    public void setType(final Type type) {
        if (type == null || type == Type.NORMAL) {
            this.removeAttribute("type");
        } else {
            this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
        }
    }

    public void setBody(final String text) {
        this.addExtension(new Body(text));
    }

    public void setAxolotlMessage(Element axolotlMessage) {
        this.children.remove(findChild("body"));
        this.children.add(0, axolotlMessage);
    }

    public enum Type {
        ERROR,
        NORMAL,
        GROUPCHAT,
        HEADLINE,
        CHAT
    }
}
