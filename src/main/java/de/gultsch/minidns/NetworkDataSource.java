package de.gultsch.minidns;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import eu.siacs.conversations.Config;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.minidns.MiniDnsException;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsname.InvalidDnsNameException;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.dnsqueryresult.StandardDnsQueryResult;

public class NetworkDataSource {

    public static final ExecutorService DNS_QUERY_EXECUTOR = Executors.newFixedThreadPool(12);

    protected int udpPayloadSize = 1232;

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
                            new CacheLoader<>() {
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

    public ListenableFuture<StandardDnsQueryResult> query(
            final DnsMessage message, final DNSServer dnsServer) {
        return query(message, dnsServer, new LinkedList<>(dnsServer.transports));
    }

    private ListenableFuture<StandardDnsQueryResult> query(
            final DnsMessage message,
            final DNSServer dnsServer,
            final Queue<Transport> transports) {
        if (transports.isEmpty()) {
            return Futures.immediateFailedFuture(
                    new IllegalStateException("No more transports left to try"));
        }
        final var transport = transports.poll();
        if (transport == null) {
            return Futures.immediateFailedFuture(new IllegalStateException("Transport was null"));
        }
        final var future =
                queryWithUniqueTransport(message, dnsServer.asUniqueTransport(transport));
        final var futureAsQueryResult =
                Futures.transformAsync(
                        future,
                        response -> {
                            if (response == null || response.truncated) {
                                return Futures.immediateFailedFuture(
                                        new IllegalStateException("Response null or truncated"));
                            }
                            return Futures.immediateFuture(
                                    new StandardDnsQueryResult(
                                            dnsServer.inetAddress,
                                            dnsServer.port,
                                            transportToMethod(transport),
                                            message,
                                            response));
                        },
                        MoreExecutors.directExecutor());
        return Futures.catchingAsync(
                futureAsQueryResult,
                Throwable.class,
                t -> {
                    if (transports.isEmpty()) {
                        return Futures.immediateFailedFuture(t);
                    }
                    return query(message, dnsServer, transports);
                },
                MoreExecutors.directExecutor());
    }

    private static DnsQueryResult.QueryMethod transportToMethod(final Transport transport) {
        return switch (transport) {
            case UDP -> DnsQueryResult.QueryMethod.udp;
            default -> DnsQueryResult.QueryMethod.tcp;
        };
    }

    private ListenableFuture<DnsMessage> queryWithUniqueTransport(
            final DnsMessage message, final DNSServer dnsServer) {
        final Transport transport = dnsServer.uniqueTransport();
        Log.d(Config.LOGTAG, "using " + dnsServer);
        return switch (transport) {
            case UDP -> queryUdpFuture(message, dnsServer.inetAddress, dnsServer.port);
            case TCP, TLS -> queryDnsSocket(message, dnsServer);
            default ->
                    Futures.immediateFailedFuture(
                            new IOException(
                                    String.format(
                                            "Transport %s has not been implemented", transport)));
        };
    }

    protected DnsMessage queryUdp(
            final DnsMessage message, final InetAddress address, final int port)
            throws IOException {
        final DatagramPacket request = message.asDatagram(address, port);
        final byte[] buffer = new byte[udpPayloadSize];
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DNSSocket.QUERY_TIMEOUT);
            socket.send(request);
            final DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            final DnsMessage dnsMessage = readDNSMessage(response.getData());
            if (dnsMessage.id != message.id) {
                throw new MiniDnsException.IdMismatch(message, dnsMessage);
            }
            return dnsMessage;
        }
    }

    protected ListenableFuture<DnsMessage> queryUdpFuture(
            final DnsMessage message, final InetAddress address, final int port) {
        return Futures.submit(() -> queryUdp(message, address, port), DNS_QUERY_EXECUTOR);
    }

    protected ListenableFuture<DnsMessage> queryDnsSocket(
            final DnsMessage message, final DNSServer dnsServer) {
        final DNSSocket cachedDnsSocket = socketCache.getIfPresent(dnsServer);
        if (cachedDnsSocket == null) {
            return Futures.transformAsync(
                    getDnsSocket(dnsServer),
                    socket -> socket.queryAsync(message),
                    MoreExecutors.directExecutor());
        }
        final var futureOnCached = cachedDnsSocket.queryAsync(message);
        return Futures.catchingAsync(
                futureOnCached,
                IOException.class,
                ex -> {
                    socketCache.invalidate(dnsServer);
                    return Futures.transformAsync(
                            getDnsSocket(dnsServer),
                            socket -> socket.queryAsync(message),
                            MoreExecutors.directExecutor());
                },
                MoreExecutors.directExecutor());
    }

    private ListenableFuture<DNSSocket> getDnsSocket(final DNSServer dnsServer) {
        return Futures.submit(() -> socketCache.get(dnsServer), DNS_QUERY_EXECUTOR);
    }

    public static DnsMessage readDNSMessage(final byte[] bytes) throws IOException {
        try {
            return new DnsMessage(bytes);
        } catch (final InvalidDnsNameException | IllegalArgumentException e) {
            throw new IOException(Throwables.getRootCause(e));
        }
    }

    public int getUdpPayloadSize() {
        return udpPayloadSize;
    }
}
