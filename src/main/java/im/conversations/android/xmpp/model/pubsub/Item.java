package im.conversations.android.xmpp.model.pubsub;

import im.conversations.android.xmpp.model.Extension;

public interface Item {

    <T extends Extension> T getExtension(final Class<T> clazz);

    String getId();
}
