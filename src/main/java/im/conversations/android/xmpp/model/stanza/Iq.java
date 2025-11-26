package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;

import eu.siacs.conversations.xml.Element;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.error.Error;

import java.util.Locale;

@XmlElement
public class Iq extends Stanza {

    public static Iq TIMEOUT = new Iq(Type.TIMEOUT);

    public Iq() {
        super(Iq.class);
    }

    public Iq(final Type type) {
        super(Iq.class);
        this.setAttribute("type", type.toString().toLowerCase(Locale.ROOT));
    }

    // TODO get rid of timeout
    public enum Type {
        SET,
        GET,
        ERROR,
        RESULT,
        TIMEOUT
    }

    public Type getType() {
        return Type.valueOf(
                Strings.nullToEmpty(this.getAttribute("type")).toUpperCase(Locale.ROOT));
    }

    @Override
    public boolean isInvalid() {
        final var id = getId();
        if (Strings.isNullOrEmpty(id)) {
            return true;
        }
        return super.isInvalid();
    }

    // Legacy methods that need to be refactored:

    public Element query() {
        final Element query = findChild("query");
        if (query != null) {
            return query;
        }
        return addChild("query");
    }

    public Element query(final String xmlns) {
        final Element query = query();
        query.setAttribute("xmlns", xmlns);
        return query();
    }

    public Iq generateResponse(final Iq.Type type) {
        final var packet = new Iq(type);
        packet.setTo(this.getFrom());
        packet.setId(this.getId());
        return packet;
    }

    public String getErrorCondition() {
        final Error error = getError();
        final var condition = error == null ? null : error.getCondition();
        return condition == null ? null : condition.getName();
    }
}
