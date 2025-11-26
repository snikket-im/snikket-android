package im.conversations.android.xmpp.model.upload;

import com.google.common.base.Strings;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import okhttp3.HttpUrl;

@XmlElement
public class Get extends Extension {

    public Get() {
        super(Get.class);
    }

    public HttpUrl getUrl() {
        final var url = this.getAttribute("url");
        if (Strings.isNullOrEmpty(url)) {
            return null;
        }
        return HttpUrl.parse(url);
    }
}
