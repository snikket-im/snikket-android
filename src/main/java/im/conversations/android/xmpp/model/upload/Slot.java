package im.conversations.android.xmpp.model.upload;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import okhttp3.HttpUrl;

@XmlElement
public class Slot extends Extension {

    public Slot() {
        super(Slot.class);
    }

    public HttpUrl getGetUrl() {
        final var get = getExtension(Get.class);
        return get == null ? null : get.getUrl();
    }

    public Put getPut() {
        return getExtension(Put.class);
    }
}
