package im.conversations.android.xmpp.model.axolotl;

import com.google.common.collect.Iterables;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyRecord;

@XmlElement
public class Bundle extends Extension {

    public Bundle() {
        super(Bundle.class);
    }

    public SignedPreKey getSignedPreKey() {
        return this.getExtension(SignedPreKey.class);
    }

    public SignedPreKeySignature getSignedPreKeySignature() {
        return this.getExtension(SignedPreKeySignature.class);
    }

    public IdentityKey getIdentityKey() {
        return this.getExtension(IdentityKey.class);
    }

    public PreKey getRandomPreKey() {
        final var preKeys = this.getExtension(PreKeys.class);
        final Collection<PreKey> preKeyList =
                preKeys == null ? Collections.emptyList() : preKeys.getExtensions(PreKey.class);
        return Iterables.get(preKeyList, (int) (preKeyList.size() * Math.random()), null);
    }

    public void setIdentityKey(final ECPublicKey ecPublicKey) {
        final var identityKey = this.addExtension(new IdentityKey());
        identityKey.setContent(ecPublicKey);
    }

    public void setSignedPreKey(
            final int id, final ECPublicKey ecPublicKey, final byte[] signature) {
        final var signedPreKey = this.addExtension(new SignedPreKey());
        signedPreKey.setId(id);
        signedPreKey.setContent(ecPublicKey);
        final var signedPreKeySignature = this.addExtension(new SignedPreKeySignature());
        signedPreKeySignature.setContent(signature);
    }

    public void addPreKeys(final List<PreKeyRecord> preKeyRecords) {
        final var preKeys = this.addExtension(new PreKeys());
        for (final PreKeyRecord preKeyRecord : preKeyRecords) {
            final var preKey = preKeys.addExtension(new PreKey());
            preKey.setId(preKeyRecord.getId());
            preKey.setContent(preKeyRecord.getKeyPair().getPublicKey());
        }
    }
}
