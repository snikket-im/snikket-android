package im.conversations.android.xmpp.model.mam;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.forward.Forwarded;

@XmlElement
public class Result extends Extension {

    public Result() {
        super(Result.class);
    }

    public Forwarded getForwarded() {
        return this.getExtension(Forwarded.class);
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public String getQueryId() {
        return this.getAttribute("queryid");
    }
}
