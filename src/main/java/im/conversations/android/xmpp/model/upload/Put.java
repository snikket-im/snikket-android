package im.conversations.android.xmpp.model.upload;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import okhttp3.HttpUrl;

@XmlElement
public class Put extends Extension {

    private static final List<String> HEADER_ALLOW_LIST =
            Arrays.asList("Authorization", "Cookie", "Expires");

    public Put() {
        super(Put.class);
    }

    public HttpUrl getUrl() {
        final var url = this.getAttribute("url");
        if (Strings.isNullOrEmpty(url)) {
            return null;
        }
        return HttpUrl.parse(url);
    }

    public Collection<Header> getHeaders() {
        return this.getExtensions(Header.class);
    }

    public Map<String, String> getHeadersAllowList() {
        final var headers = new ImmutableMap.Builder<String, String>();
        for (final Header header : this.getHeaders()) {
            final String name = header.getHeaderName();
            final String value = Strings.nullToEmpty(header.getContent()).trim();
            if (Strings.isNullOrEmpty(value) || value.contains("\n")) {
                continue;
            }
            if (HEADER_ALLOW_LIST.contains(name)) {
                headers.put(name, value);
            }
        }
        return headers.buildKeepingLast();
    }
}
