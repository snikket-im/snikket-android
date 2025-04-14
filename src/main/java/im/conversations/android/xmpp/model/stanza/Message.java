package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.LocalizedContent;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.jabber.Body;
import im.conversations.android.xmpp.model.jabber.Subject;
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
        return getLocalizedContent(Body.class);
    }

    public LocalizedContent getSubject() {
        return getLocalizedContent(Subject.class);
    }

    private LocalizedContent getLocalizedContent(final Class<? extends Extension> clazz) {
        final var builder = new ImmutableMap.Builder<String, String>();
        final var messageLanguage = this.getAttribute("xml:lang");
        final var parentLanguage =
                Strings.isNullOrEmpty(messageLanguage)
                        ? LocalizedContent.STREAM_LANGUAGE
                        : messageLanguage;
        for (final var element : this.getExtensions(clazz)) {
            final var elementLanguage = element.getAttribute("xml:lang");
            final var language =
                    Strings.isNullOrEmpty(elementLanguage) ? parentLanguage : elementLanguage;
            final var content = element.getContent();
            if (content == null) {
                continue;
            }
            builder.put(language, content);
        }
        try {
            return LocalizedContent.get(builder.buildOrThrow());
        } catch (final IllegalArgumentException e) {
            return null;
        }
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
