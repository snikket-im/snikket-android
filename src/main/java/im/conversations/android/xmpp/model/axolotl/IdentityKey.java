package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "identityKey")
public class IdentityKey extends Extension implements ECPublicKeyContent {

    public IdentityKey() {
        super(IdentityKey.class);
    }
}
