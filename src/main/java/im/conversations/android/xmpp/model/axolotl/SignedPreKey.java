package im.conversations.android.xmpp.model.axolotl;

import com.google.common.primitives.Ints;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "signedPreKeyPublic")
public class SignedPreKey extends Extension implements ECPublicKeyContent {

    public SignedPreKey() {
        super(SignedPreKey.class);
    }

    public int getId() {
        return Ints.saturatedCast(this.getLongAttribute("signedPreKeyId"));
    }

    public void setId(final int id) {
        this.setAttribute("signedPreKeyId", id);
    }
}
