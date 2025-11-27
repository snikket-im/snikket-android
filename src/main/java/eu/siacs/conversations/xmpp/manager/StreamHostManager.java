package eu.siacs.conversations.xmpp.manager;

import android.content.Context;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.transports.SocksByteStreamsTransport;
import im.conversations.android.xmpp.model.socks5.Query;
import im.conversations.android.xmpp.model.stanza.Iq;
import java.util.UUID;

public class StreamHostManager extends AbstractManager {

    public StreamHostManager(final Context context, final XmppConnection connection) {
        super(context, connection);
    }

    public ListenableFuture<SocksByteStreamsTransport.Candidate> getProxyCandidate(
            final boolean asInitiator) {
        if (Config.DISABLE_PROXY_LOOKUP) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("Proxy look up is disabled"));
        }
        final var streamer =
                getManager(DiscoManager.class).findDiscoItemByFeature(Namespace.BYTE_STREAMS);
        if (streamer == null) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No proxy/streamer found"));
        }
        return getProxyCandidate(asInitiator, streamer.getKey());
    }

    private ListenableFuture<SocksByteStreamsTransport.Candidate> getProxyCandidate(
            final boolean asInitiator, final Jid streamer) {
        final var iq = new Iq(Iq.Type.GET, new Query());
        iq.setTo(streamer);
        return Futures.transform(
                connection.sendIqPacket(iq),
                response -> {
                    final var query = response.getExtension(Query.class);
                    if (query == null) {
                        throw new IllegalStateException("No stream host query found in response");
                    }
                    final var streamHost = query.getStreamHost();
                    if (streamHost == null) {
                        throw new IllegalStateException("no stream host found in query");
                    }
                    final var jid = streamHost.getJid();
                    final var host = streamHost.getHost();
                    final var port = streamHost.getPort();
                    if (jid == null || host == null || port == null) {
                        throw new IllegalStateException("StreamHost had incomplete information");
                    }
                    return new SocksByteStreamsTransport.Candidate(
                            UUID.randomUUID().toString(),
                            host,
                            streamer,
                            port,
                            655360 + (asInitiator ? 0 : 15),
                            SocksByteStreamsTransport.CandidateType.PROXY);
                },
                MoreExecutors.directExecutor());
    }
}
