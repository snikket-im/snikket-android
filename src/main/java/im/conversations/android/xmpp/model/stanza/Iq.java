package im.conversations.android.xmpp.model.stanza;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.error.Error;
import java.util.Arrays;
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

    public Iq(final Type type, final Extension... extensions) {
        this(type);
        this.addExtensions(Arrays.asList(extensions));
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
