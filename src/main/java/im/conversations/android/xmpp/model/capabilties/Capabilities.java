package im.conversations.android.xmpp.model.capabilties;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.io.BaseEncoding;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.model.Extension;
import im.conversations.android.xmpp.model.Hash;

@XmlElement(name = "c", namespace = Namespace.ENTITY_CAPABILITIES_2)
public class Capabilities extends Extension {

    public Capabilities() {
        super(Capabilities.class);
    }

    public EntityCapabilities2.EntityCaps2Hash getHash() {
        final Optional<Hash> sha256Hash =
                Iterables.tryFind(
                        getExtensions(Hash.class), h -> h.getAlgorithm() == Hash.Algorithm.SHA_256);
        if (sha256Hash.isPresent()) {
            final String content = sha256Hash.get().getContent();
            if (Strings.isNullOrEmpty(content)) {
                return null;
            }
            if (BaseEncoding.base64().canDecode(content)) {
                return EntityCapabilities2.EntityCaps2Hash.of(Hash.Algorithm.SHA_256, content);
            }
        }
        return null;
    }

    public void setHash(final EntityCapabilities2.EntityCaps2Hash caps2Hash) {
        final Hash hash = new Hash();
        hash.setAlgorithm(caps2Hash.algorithm);
        hash.setContent(caps2Hash.encoded());
        this.addExtension(hash);
    }
}
