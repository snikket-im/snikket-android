package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.IP;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import org.webrtc.PeerConnection;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class IceServers {

    public static List<PeerConnection.IceServer> parse(final IqPacket response) {
        ImmutableList.Builder<PeerConnection.IceServer> listBuilder = new ImmutableList.Builder<>();
        if (response.getType() == IqPacket.TYPE.RESULT) {
            final Element services =
                    response.findChild("services", Namespace.EXTERNAL_SERVICE_DISCOVERY);
            final List<Element> children =
                    services == null ? Collections.emptyList() : services.getChildren();
            for (final Element child : children) {
                if ("service".equals(child.getName())) {
                    final String type = child.getAttribute("type");
                    final String host = child.getAttribute("host");
                    final String sport = child.getAttribute("port");
                    final Integer port = sport == null ? null : Ints.tryParse(sport);
                    final String transport = child.getAttribute("transport");
                    final String username = child.getAttribute("username");
                    final String password = child.getAttribute("password");
                    if (Strings.isNullOrEmpty(host) || port == null) {
                        continue;
                    }
                    if (port < 0 || port > 65535) {
                        continue;
                    }

                    if (Arrays.asList("stun", "stuns", "turn", "turns").contains(type)
                            && Arrays.asList("udp", "tcp").contains(transport)) {
                        if (Arrays.asList("stuns", "turns").contains(type)
                                && "udp".equals(transport)) {
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
                            // InvalidAccessError when username (from libwebrtc
                            // source coder)
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
                        final PeerConnection.IceServer iceServer =
                                iceServerBuilder.createIceServer();
                        Log.w(Config.LOGTAG, "discovered ICE Server: " + iceServer);
                        listBuilder.add(iceServer);
                    }
                }
            }
        }
        return listBuilder.build();
    }
}
