package im.conversations.android.xmpp.model.upload.purpose;

import im.conversations.android.xmpp.model.Extension;

public abstract class Purpose extends Extension {

    protected Purpose(final Class<? extends Purpose> clazz) {
        super(clazz);
    }
}
