package de.gultsch.minidns;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

public final class DNSServer {

    public final InetAddress inetAddress;
    public final String hostname;
    public final int port;
    public final List<Transport> transports;

    public DNSServer(InetAddress inetAddress, Integer port, Transport transport) {
        this.inetAddress = inetAddress;
        this.port = port == null ? 0 : port;
        this.transports = Collections.singletonList(transport);
        this.hostname = null;
    }

    public DNSServer(final String hostname, final Integer port, final Transport transport) {
        Preconditions.checkArgument(
                Arrays.asList(Transport.HTTPS, Transport.TLS).contains(transport),
                "hostname validation only works with TLS based transports");
        this.hostname = hostname;
        this.port = port == null ? 0 : port;
        this.transports = Collections.singletonList(transport);
        this.inetAddress = null;
    }

    public DNSServer(final String hostname, final Transport transport) {
        this(hostname, Transport.DEFAULT_PORTS.get(transport), transport);
    }

    public DNSServer(InetAddress inetAddress, Transport transport) {
        this(inetAddress, Transport.DEFAULT_PORTS.get(transport), transport);
    }

    public DNSServer(final InetAddress inetAddress) {
        this(inetAddress, 53, Arrays.asList(Transport.UDP, Transport.TCP));
    }

    public DNSServer(final InetAddress inetAddress, int port, List<Transport> transports) {
        this(inetAddress, null, port, transports);
    }

    private DNSServer(
            final InetAddress inetAddress,
            final String hostname,
            final int port,
            final List<Transport> transports) {
        this.inetAddress = inetAddress;
        this.hostname = hostname;
        this.port = port;
        this.transports = transports;
    }

    public Transport uniqueTransport() {
        return Iterables.getOnlyElement(this.transports);
    }

    public DNSServer asUniqueTransport(final Transport transport) {
        Preconditions.checkArgument(
                this.transports.contains(transport),
                "This DNS server does not have transport ",
                transport);
        return new DNSServer(inetAddress, hostname, port, Collections.singletonList(transport));
    }

    @Override
    @Nonnull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("inetAddress", inetAddress)
                .add("hostname", hostname)
                .add("port", port)
                .add("transports", transports)
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DNSServer dnsServer = (DNSServer) o;
        return port == dnsServer.port
                && Objects.equal(inetAddress, dnsServer.inetAddress)
                && Objects.equal(hostname, dnsServer.hostname)
                && Objects.equal(transports, dnsServer.transports);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(inetAddress, hostname, port, transports);
    }
}
