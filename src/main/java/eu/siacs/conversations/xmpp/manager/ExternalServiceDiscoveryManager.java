package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.XmppConnection;
import im.conversations.android.xmpp.model.disco.external.Services;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.Collection;
import org.webrtc.PeerConnection;

public class ExternalServiceDiscoveryManager extends AbstractManager {

    public ExternalServiceDiscoveryManager(Context context, XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<Collection<PeerConnection.IceServer>> getIceServers() {
        if (hasFeature()) {
            return Futures.transform(
                    getServices(), Services::getIceServers, MoreExecutors.directExecutor());
        } else {
            return Futures.immediateFailedFuture(
                    new IllegalStateException(
                            "Server has no support for external service discovery"));
        }
    }

    public ListenableFuture<Services> getServices() {
        final Iq request = new Iq(Iq.Type.GET);
        request.setTo(getAccount().getDomain());
        request.addExtension(new Services());
        return Futures.transform(
                this.connection.sendIqPacket(request),
                response -> {
                    final var services = response.getExtension(Services.class);
                    if (services == null) {
                        throw new IllegalStateException("Response did not contain services");
                    }
                    return services;
                },
                MoreExecutors.directExecutor());
    }

    public boolean hasFeature() {
        return getManager(DiscoManager.class)
                .hasServerFeature(Namespace.EXTERNAL_SERVICE_DISCOVERY);
    }
}
