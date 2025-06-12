package im.conversations.android.xmpp.model.bob;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement
public class Data extends Extension implements ByteContent {

    public Data() {
        super(Data.class);
    }

    public String getCid() {
        return this.getAttribute("cid");
    }

    public String getType() {
        return this.getAttribute("type");
    }

    public static Optional<Data> get(final Extension stanza, final String cid) {
        return Iterables.tryFind(stanza.getExtensions(Data.class), d -> cid.equals(d.getCid()));
    }
}
