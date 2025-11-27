package im.conversations.android.xmpp.model.upload;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Request extends Extension {

    public Request() {
        super(Request.class);
    }

    public void setFilename(final String filename) {
        this.setAttribute("filename", filename);
    }

    public void setSize(final long size) {
        this.setAttribute("size", size);
    }

    public void setContentType(final String type) {
        this.setAttribute("content-type", type);
    }
}
