package im.conversations.android.xmpp.model.disco.external;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.IP;
import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;
import java.util.Arrays;
import java.util.Collection;
import org.webrtc.PeerConnection;

@XmlElement
public class Services extends Extension {

    public Services() {
        super(Services.class);
    }

    public Collection<Service> getServices() {
        return this.getExtensions(Service.class);
    }

    public Collection<PeerConnection.IceServer> getIceServers() {
        final var builder = new ImmutableSet.Builder<IceServerWrapper>();
        for (final var service : this.getServices()) {
            final String type = service.getAttribute("type");
            final String host = service.getAttribute("host");
            final String sport = service.getAttribute("port");
            final Integer port = sport == null ? null : Ints.tryParse(sport);
            final String transport = service.getAttribute("transport");
            final String username = service.getAttribute("username");
            final String password = service.getAttribute("password");
            if (Strings.isNullOrEmpty(host) || port == null) {
                continue;
            }
            if (port < 0 || port > 65535) {
                continue;
            }

            if (Arrays.asList("stun", "stuns", "turn", "turns").contains(type)
                    && Arrays.asList("udp", "tcp").contains(transport)) {
                if (Arrays.asList("stuns", "turns").contains(type) && "udp".equals(transport)) {
                    Log.w(
                            Config.LOGTAG,
                            "skipping invalid combination of udp/tls in external services");
                    continue;
                }

                // STUN URLs do not support a query section since M110
                final String uri;
                if (Arrays.asList("stun", "stuns").contains(type)) {
                    uri = String.format("%s:%s:%s", type, IP.wrapIPv6(host), port);
                } else {
                    uri =
                            String.format(
                                    "%s:%s:%s?transport=%s",
                                    type, IP.wrapIPv6(host), port, transport);
                }

                final PeerConnection.IceServer.Builder iceServerBuilder =
                        PeerConnection.IceServer.builder(uri);
                iceServerBuilder.setTlsCertPolicy(
                        PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK);
                if (username != null && password != null) {
                    iceServerBuilder.setUsername(username);
                    iceServerBuilder.setPassword(password);
                } else if (Arrays.asList("turn", "turns").contains(type)) {
                    // The WebRTC spec requires throwing an
                    // InvalidAccessError on empty username or password
                    // https://chromium.googlesource.com/external/webrtc/+/master/pc/ice_server_parsing.cc
                    Log.w(
                            Config.LOGTAG,
                            "skipping "
                                    + type
                                    + "/"
                                    + transport
                                    + " without username and password");
                    continue;
                }
                final var iceServer = new IceServerWrapper(iceServerBuilder.createIceServer());
                Log.w(Config.LOGTAG, "discovered ICE Server: " + iceServer);
                builder.add(iceServer);
            }
        }
        final var set = builder.build();
        Log.d(Config.LOGTAG, "discovered " + set.size() + " ice servers");
        return Collections2.transform(set, i -> i.iceServer);
    }

    private static class IceServerWrapper {

        private final PeerConnection.IceServer iceServer;

        private IceServerWrapper(final PeerConnection.IceServer iceServer) {
            this.iceServer = iceServer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IceServerWrapper that)) return false;
            return Objects.equal(iceServer.urls, that.iceServer.urls)
                    && Objects.equal(iceServer.username, that.iceServer.username)
                    && Objects.equal(iceServer.password, that.iceServer.password);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(iceServer.urls, iceServer.urls, iceServer.password);
        }

        @Override
        @NonNull
        public String toString() {
            return this.iceServer.toString();
        }
    }
}
