package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.EntityCapabilities;
import im.conversations.android.xmpp.EntityCapabilities2;
import im.conversations.android.xmpp.ServiceDescription;
import im.conversations.android.xmpp.model.capabilties.Capabilities;
import im.conversations.android.xmpp.model.capabilties.LegacyCapabilities;
import im.conversations.android.xmpp.model.pgp.Signed;
import im.conversations.android.xmpp.model.stanza.Presence;
import java.util.HashMap;
import java.util.Map;

public class PresenceManager extends AbstractManager {

    private final Map<EntityCapabilities.Hash, ServiceDescription> serviceDescriptions =
            new HashMap<>();

    public PresenceManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public Presence getPresence(final Presence.Availability availability, final boolean personal) {
        final var account = connection.getAccount();
        final var serviceDiscoveryFeatures = getManager(DiscoManager.class).getServiceDescription();
        final var infoQuery = serviceDiscoveryFeatures.asInfoQuery();
        final var capsHash = EntityCapabilities.hash(infoQuery);
        final var caps2Hash = EntityCapabilities2.hash(infoQuery);
        serviceDescriptions.put(capsHash, serviceDiscoveryFeatures);
        serviceDescriptions.put(caps2Hash, serviceDiscoveryFeatures);
        final var capabilities = new Capabilities();
        capabilities.setHash(caps2Hash);
        final var legacyCapabilities = new LegacyCapabilities();
        legacyCapabilities.setNode(DiscoManager.CAPABILITY_NODE);
        legacyCapabilities.setHash(capsHash);
        final var presence = new Presence();
        presence.addExtension(capabilities);
        presence.addExtension(legacyCapabilities);

        if (personal) {
            final String pgpSignature = account.getPgpSignature();
            final String message = account.getPresenceStatusMessage();
            presence.setAvailability(availability);
            presence.setStatus(message);
            if (pgpSignature != null) {
                final var signed = new Signed();
                signed.setContent(pgpSignature);
                presence.addExtension(new Signed());
            }
        }
        return presence;
    }

    public ServiceDescription getCachedServiceDescription(final EntityCapabilities.Hash hash) {
        return this.serviceDescriptions.get(hash);
    }
}
