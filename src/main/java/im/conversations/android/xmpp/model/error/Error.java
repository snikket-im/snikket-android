package im.conversations.android.xmpp.model.error;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Locale;
import eu.siacs.conversations.xml.Namespace;

@XmlElement(namespace = Namespace.JABBER_CLIENT)
public class Error extends Extension {

    public Error() {
        super(Error.class);
    }

    public Condition getCondition() {
        return this.getExtension(Condition.class);
    }

    public void setCondition(final Condition condition) {
        this.addExtension(condition);
    }

    public Text getText() {
        return this.getExtension(Text.class);
    }

    public String getTextAsString() {
        final var text = getText();
        return text == null ? null : text.getContent();
    }

    public void setType(final Type type) {
        this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
    }

    public void addExtensions(final Extension[] extensions) {
        for (final Extension extension : extensions) {
            this.addExtension(extension);
        }
    }

    public enum Type {
        MODIFY,
        CANCEL,
        AUTH,
        WAIT
    }

    public static class Extension extends im.conversations.android.xmpp.model.Extension {

        public Extension(Class<? extends im.conversations.android.xmpp.model.Extension> clazz) {
            super(clazz);
        }
    }
}
