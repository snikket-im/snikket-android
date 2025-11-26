package im.conversations.android.xmpp.model.mds;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Namespace.MDS_DISPLAYED)
public class Displayed extends Extension {
    public Displayed() {
        super(Displayed.class);
    }
}
