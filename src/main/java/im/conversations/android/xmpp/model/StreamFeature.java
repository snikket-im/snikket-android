package im.conversations.android.xmpp.model;

public abstract class StreamFeature extends Extension{

    public StreamFeature(Class<? extends StreamFeature> clazz) {
        super(clazz);
    }
}
