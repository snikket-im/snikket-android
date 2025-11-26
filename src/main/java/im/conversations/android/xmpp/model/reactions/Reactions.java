package im.conversations.android.xmpp.model.reactions;

import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Objects;

@XmlElement
public class Reactions extends Extension {

    public Reactions() {
        super(Reactions.class);
    }

    public Collection<String> getReactions() {
        return Collections2.filter(
                Collections2.transform(getExtensions(Reaction.class), Reaction::getContent),
                r -> Objects.nonNull(Strings.nullToEmpty(r)));
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(String id) {
        this.setAttribute("id", id);
    }

    public static Reactions to(final String id) {
        final var reactions = new Reactions();
        reactions.setId(id);
        return reactions;
    }
}
