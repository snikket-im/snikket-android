package im.conversations.android.xmpp.model.markers;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;

@XmlElement
public class Markable extends DeliveryReceiptRequest {

    public Markable() {
        super(Markable.class);
    }
}
