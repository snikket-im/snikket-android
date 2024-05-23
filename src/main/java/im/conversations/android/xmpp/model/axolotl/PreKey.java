package im.conversations.android.xmpp.model.axolotl;

import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "preKeyPublic")
public class PreKey extends Extension implements ECPublicKeyContent {

    public PreKey() {
        super(PreKey.class);
    }

    public int getId() {
        return Ints.saturatedCast(this.getLongAttribute("preKeyId"));
    }

    public void setId(int id) {
        this.setAttribute("preKeyId", id);
    }
}
