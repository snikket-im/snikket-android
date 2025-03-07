package eu.siacs.conversations.xmpp.jingle;

import im.conversations.android.xmpp.model.disco.external.Services;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Collections;
import java.util.Set;
import org.webrtc.PeerConnection;

public final class IceServers {

    public static Set<PeerConnection.IceServer> parse(final Iq response) {
        if (response.getType() != Iq.Type.RESULT) {
            return Collections.emptySet();
        }
        final var services = response.getExtension(Services.class);
        if (services == null) {
            return Collections.emptySet();
        }
        return services.getIceServers();
    }
}
