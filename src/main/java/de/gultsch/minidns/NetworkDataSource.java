package de.gultsch.minidns;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.ImmutableList;

import de.measite.minidns.DNSMessage;
import de.measite.minidns.MiniDNSException;
import de.measite.minidns.source.DNSDataSource;
import de.measite.minidns.util.MultipleIoException;

import eu.siacs.conversations.Config;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class NetworkDataSource extends DNSDataSource {

    private static final LoadingCache<DNSServer, DNSSocket> socketCache =
            CacheBuilder.newBuilder()
                    .removalListener(
                            (RemovalListener<DNSServer, DNSSocket>)
                                    notification -> {
                                        final DNSServer dnsServer = notification.getKey();
                                        final DNSSocket dnsSocket = notification.getValue();
                                        if (dnsSocket == null) {
                                            return;
                                        }
                                        Log.d(Config.LOGTAG, "closing connection to " + dnsServer);
                                        dnsSocket.closeQuietly();
                                    })
                    .expireAfterAccess(5, TimeUnit.MINUTES)
                    .build(
                            new CacheLoader<DNSServer, DNSSocket>() {
                                @Override
                                @NonNull
                                public DNSSocket load(@NonNull final DNSServer dnsServer)
                                        throws Exception {
                                    Log.d(Config.LOGTAG, "establishing connection to " + dnsServer);
                                    return DNSSocket.connect(dnsServer);
                                }
                            });

    private static List<Transport> transportsForPort(final int port) {
        final ImmutableList.Builder<Transport> transportBuilder = new ImmutableList.Builder<>();
        for (final Map.Entry<Transport, Integer> entry : Transport.DEFAULT_PORTS.entrySet()) {
            if (entry.getValue().equals(port)) {
                transportBuilder.add(entry.getKey());
            }
        }
        return transportBuilder.build();
    }

    @Override
    public DNSMessage query(final DNSMessage message, final InetAddress address, final int port)
            throws IOException {
        final List<Transport> transports = transportsForPort(port);
        Log.w(
                Config.LOGTAG,
                "using legacy DataSource interface. guessing transports "
                        + transports
                        + " from port");
        if (transports.isEmpty()) {
            throw new IOException(String.format("No transports found for port %d", port));
        }
        return query(message, new DNSServer(address, port, transports));
    }

    public DNSMessage query(final DNSMessage message, final DNSServer dnsServer)
            throws IOException {
        Log.d(Config.LOGTAG, "using " + dnsServer);
        final List<IOException> ioExceptions = new ArrayList<>();
        for (final Transport transport : dnsServer.transports) {
            try {
                final DNSMessage response =
                        queryWithUniqueTransport(message, dnsServer.asUniqueTransport(transport));
                if (response != null && !response.truncated) {
                    return response;
                }
            } catch (final IOException e) {
                ioExceptions.add(e);
            } catch (final InterruptedException e) {
                return null;
            }
        }
        MultipleIoException.throwIfRequired(ioExceptions);
        return null;
    }

    private DNSMessage queryWithUniqueTransport(final DNSMessage message, final DNSServer dnsServer)
            throws IOException, InterruptedException {
        final Transport transport = dnsServer.uniqueTransport();
        switch (transport) {
            case UDP:
                return queryUdp(message, dnsServer.inetAddress, dnsServer.port);
            case TCP:
            case TLS:
                return queryDnsSocket(message, dnsServer);
            default:
                throw new IOException(
                        String.format("Transport %s has not been implemented", transport));
        }
    }

    protected DNSMessage queryUdp(
            final DNSMessage message, final InetAddress address, final int port)
            throws IOException {
        final DatagramPacket request = message.asDatagram(address, port);
        final byte[] buffer = new byte[udpPayloadSize];
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeout);
            socket.send(request);
            final DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            DNSMessage dnsMessage = new DNSMessage(response.getData());
            if (dnsMessage.id != message.id) {
                throw new MiniDNSException.IdMismatch(message, dnsMessage);
            }
            return dnsMessage;
        }
    }

    protected DNSMessage queryDnsSocket(final DNSMessage message, final DNSServer dnsServer)
            throws IOException, InterruptedException {
        final DNSSocket cachedDnsSocket = socketCache.getIfPresent(dnsServer);
        if (cachedDnsSocket != null) {
            try {
                return cachedDnsSocket.query(message);
            } catch (final IOException e) {
                Log.d(
                        Config.LOGTAG,
                        "IOException occurred at cached socket. invalidating and falling through to new socket creation");
                socketCache.invalidate(dnsServer);
            }
        }
        try {
            return socketCache.get(dnsServer).query(message);
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException(cause);
            }
        }
    }
}
