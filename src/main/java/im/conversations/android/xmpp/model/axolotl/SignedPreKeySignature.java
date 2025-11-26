package im.conversations.android.xmpp.model.axolotl;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.ByteContent;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "signedPreKeySignature")
public class SignedPreKeySignature extends Extension implements ByteContent {

    public SignedPreKeySignature() {
        super(SignedPreKeySignature.class);
    }
}
