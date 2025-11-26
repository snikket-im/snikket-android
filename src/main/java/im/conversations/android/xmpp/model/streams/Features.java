package im.conversations.android.xmpp.model.streams;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.StreamElement;
import im.conversations.android.xmpp.model.StreamFeature;
import im.conversations.android.xmpp.model.capabilties.EntityCapabilities;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.xmpp.model.sm.StreamManagement;

@XmlElement
public class Features extends StreamElement implements EntityCapabilities {
    public Features() {
        super(Features.class);
    }

    public boolean streamManagement() {
        return hasStreamFeature(StreamManagement.class);
    }

    public boolean invite() {
        return this.hasChild("register", Namespace.INVITE);
    }

    public boolean clientStateIndication() {
        return this.hasChild("csi", Namespace.CSI);
    }


    public boolean hasStreamFeature(final Class<? extends StreamFeature> clazz) {
        return hasExtension(clazz);
    }
}
