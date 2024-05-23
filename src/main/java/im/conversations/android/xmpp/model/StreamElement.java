package im.conversations.android.xmpp.model;

public abstract class StreamElement extends Extension {

    protected StreamElement(Class<? extends StreamElement> clazz) {
        super(clazz);
    }
}
