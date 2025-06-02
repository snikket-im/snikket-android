package im.conversations.android.xmpp.model.socks5;

import eu.siacs.conversations.xmpp.Jid;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "streamhost")
public class StreamHost extends Extension {

    public StreamHost() {
        super(StreamHost.class);
    }

    public Jid getJid() {
        return this.getAttributeAsJid("jid");
    }

    public String getHost() {
        return this.getAttribute("host");
    }

    public Integer getPort() {
        return this.getOptionalIntAttribute("port").orNull();
    }
}
