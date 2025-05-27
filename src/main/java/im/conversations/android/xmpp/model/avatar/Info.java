package im.conversations.android.xmpp.model.avatar;

import com.google.common.base.Strings;
import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import okhttp3.HttpUrl;

@XmlElement(namespace = Namespace.AVATAR_METADATA)
public class Info extends Extension {

    public Info() {
        super(Info.class);
    }

    public Info(
            final String id,
            final long bytes,
            final String type,
            final int height,
            final int width) {
        this();
        this.setId(id);
        this.setBytes(bytes);
        this.setType(type);
        this.setHeight(height);
        this.setWidth(width);
    }

    public long getHeight() {
        return this.getLongAttribute("height");
    }

    public long getWidth() {
        return this.getLongAttribute("width");
    }

    public long getBytes() {
        return this.getLongAttribute("bytes");
    }

    public String getType() {
        return this.getAttribute("type");
    }

    public HttpUrl getUrl() {
        final var url = this.getAttribute("url");
        if (Strings.isNullOrEmpty(url)) {
            return null;
        }
        return HttpUrl.parse(url);
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setBytes(final long size) {
        this.setAttribute("bytes", size);
    }

    public void setId(final String id) {
        this.setAttribute("id", id);
    }

    public void setHeight(final long height) {
        this.setAttribute("height", height);
    }

    public void setWidth(final long width) {
        this.setAttribute("width", width);
    }

    public void setType(final String type) {
        this.setAttribute("type", type);
    }

    public void setUrl(final HttpUrl url) {
        this.setAttribute("url", url.toString());
    }
}
