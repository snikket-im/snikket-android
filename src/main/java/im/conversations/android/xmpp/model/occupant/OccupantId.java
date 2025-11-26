package im.conversations.android.xmpp.model.occupant;

import com.google.common.base.Strings;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.OCCUPANT_ID)
public class OccupantId extends Extension {

    public OccupantId() {
        super(OccupantId.class);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }
}
