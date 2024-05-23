package im.conversations.android.xmpp.model.csi;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.StreamFeature;

@XmlElement(name = "csi")
public class ClientStateIndication extends StreamFeature {

    public ClientStateIndication() {
        super(ClientStateIndication.class);
    }
}
