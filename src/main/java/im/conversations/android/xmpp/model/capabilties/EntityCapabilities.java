package im.conversations.android.xmpp.model.capabilties;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import im.conversations.android.xmpp.model.Extension;

public interface EntityCapabilities {

    <E extends Extension> E getExtension(final Class<E> clazz);

    default NodeHash getCapabilities() {
        final String node;
        final im.conversations.android.xmpp.EntityCapabilities.Hash hash;
        final var capabilities = this.getExtension(Capabilities.class);
        final var legacyCapabilities = this.getExtension(LegacyCapabilities.class);
        if (capabilities != null) {
            node = null;
            hash = capabilities.getHash();
        } else if (legacyCapabilities != null) {
            node = legacyCapabilities.getNode();
            hash = legacyCapabilities.getHash();
        } else {
            return null;
        }
        return hash == null ? null : new NodeHash(node, hash);
    }

    class NodeHash {
        public final String node;
        public final im.conversations.android.xmpp.EntityCapabilities.Hash hash;

        private NodeHash(
                @Nullable String node,
                @NonNull final im.conversations.android.xmpp.EntityCapabilities.Hash hash) {
            this.node = node;
            this.hash = hash;
        }
    }
}
