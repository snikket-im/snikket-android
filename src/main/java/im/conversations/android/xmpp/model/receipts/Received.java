package im.conversations.android.xmpp.model.receipts;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.DeliveryReceipt;

@XmlElement
public class Received extends DeliveryReceipt {

    public Received() {
        super(Received.class);
    }

    public void setId(String id) {
        this.setAttribute("id", id);
    }

    public String getId() {
        return this.getAttribute("id");
    }
}
