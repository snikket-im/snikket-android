package im.conversations.android.xmpp.model.sasl2;

import com.google.common.base.Strings;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class AuthorizationIdentifier extends Extension {


    public AuthorizationIdentifier() {
        super(AuthorizationIdentifier.class);
    }

    public Jid get() {
        final var content = getContent();
        if ( Strings.isNullOrEmpty(content)) {
            return null;
        }
        try {
            return Jid.ofEscaped(content);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }
}
