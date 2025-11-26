package im.conversations.android.xmpp.model.upload;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Request extends Extension {

    public Request() {
        super(Request.class);
    }

    public void setFilename(String filename) {
        this.setAttribute("filename", filename);
    }

    public void setSize(long size) {
        this.setAttribute("size", size);
    }

    public void setContentType(String type) {
        this.setAttribute("content-ype", type);
    }
}
