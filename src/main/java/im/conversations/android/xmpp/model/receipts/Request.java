package im.conversations.android.xmpp.model.receipts;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.DeliveryReceiptRequest;

@XmlElement
public class Request extends DeliveryReceiptRequest {

    public Request() {
        super(Request.class);
    }
}
