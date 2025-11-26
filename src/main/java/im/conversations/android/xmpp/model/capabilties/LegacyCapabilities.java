package im.conversations.android.xmpp.model.capabilties;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;

import eu.siacs.conversations.xml.Namespace;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(name = "c", namespace = Namespace.ENTITY_CAPABILITIES)
public class LegacyCapabilities extends Extension {

    private static final String HASH_ALGORITHM = "sha-1";

    public LegacyCapabilities() {
        super(LegacyCapabilities.class);
    }

    public String getNode() {
        return this.getAttribute("node");
    }

    public EntityCapabilities.EntityCapsHash getHash() {
        final String hash = getAttribute("hash");
        final String ver = getAttribute("ver");
        if (Strings.isNullOrEmpty(ver) || Strings.isNullOrEmpty(hash)) {
            return null;
        }
        if (HASH_ALGORITHM.equals(hash) && BaseEncoding.base64().canDecode(ver)) {
            return EntityCapabilities.EntityCapsHash.of(ver);
        } else {
            return null;
        }
    }

    public void setNode(final String node) {
        this.setAttribute("node", node);
    }

    public void setHash(final EntityCapabilities.EntityCapsHash hash) {
        this.setAttribute("hash", HASH_ALGORITHM);
        this.setAttribute("ver", hash.encoded());
    }
}
