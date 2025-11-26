package im.conversations.android.xmpp.model.unique;

import com.google.common.collect.ImmutableMap;
import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Map;

@XmlElement
public class StanzaId extends Extension {

    public StanzaId() {
        super(StanzaId.class);
    }

    public Jid getBy() {
        return this.getAttributeAsJid("by");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public static String get(
            final im.conversations.android.xmpp.model.stanza.Message packet, final Jid by) {
        final var builder = new ImmutableMap.Builder<Jid, String>();
        for (final var stanzaId : packet.getExtensions(StanzaId.class)) {
            final var id = stanzaId.getId();
            final var byAttribute = Jid.Invalid.getNullForInvalid(stanzaId.getBy());
            if (byAttribute != null && id != null) {
                builder.put(byAttribute, id);
            }
        }
        final Map<Jid, String> byToId;
        try {
            byToId = builder.buildOrThrow();
        } catch (final IllegalArgumentException e) {
            return null;
        }
        return byToId.get(by);
    }
}
