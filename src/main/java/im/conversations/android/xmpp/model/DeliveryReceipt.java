package im.conversations.android.xmpp.model;

public abstract class DeliveryReceipt extends Extension {

    protected DeliveryReceipt(Class<? extends Extension> clazz) {
        super(clazz);
    }

    public abstract String getId();
}
