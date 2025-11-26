package im.conversations.android.xmpp.model.bind;

import com.google.common.base.Strings;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Bind extends Extension {

    public Bind() {
        super(Bind.class);
    }

    public void setResource(final String resource) {
        this.addExtension(new Resource(resource));
    }

    public eu.siacs.conversations.xmpp.Jid getJid() {
        final var jidExtension = this.getExtension(Jid.class);
        if (jidExtension == null) {
            return null;
        }
        final var content = jidExtension.getContent();
        if (Strings.isNullOrEmpty(content)) {
            return null;
        }
        try {
            return eu.siacs.conversations.xmpp.Jid.ofEscaped(content);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
