package im.conversations.android.xmpp.model.unique;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class OriginId extends Extension {

    public OriginId() {
        super(OriginId.class);
    }

    public OriginId(final String id) {
        this();
        this.setAttribute("id", id);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }
}
