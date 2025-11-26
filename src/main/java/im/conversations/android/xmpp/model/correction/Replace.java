package im.conversations.android.xmpp.model.correction;

import androidx.annotation.NonNull;
import com.google.common.base.Strings;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.LAST_MESSAGE_CORRECTION)
public class Replace extends Extension {

    public Replace() {
        super(Replace.class);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }

    public void setId(@NonNull final String id) {
        this.setAttribute("id", id);
    }
}
