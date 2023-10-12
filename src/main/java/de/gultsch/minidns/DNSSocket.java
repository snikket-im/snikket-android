package de.gultsch.minidns;

import android.util.Log;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import de.measite.minidns.DNSMessage;

import eu.siacs.conversations.Config;

import org.conscrypt.OkHostnameVerifier;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

final class DNSSocket implements Closeable {

    private static final int CONNECT_TIMEOUT = 5_000;
    public static final int QUERY_TIMEOUT = 5_000;

    private final Semaphore semaphore = new Semaphore(1);
    private final Map<Integer, SettableFuture<DNSMessage>> inFlightQueries = new HashMap<>();
    private final Socket socket;
    private final DataInputStream dataInputStream;
    private final DataOutputStream dataOutputStream;

    private DNSSocket(
            final Socket socket,
            final DataInputStream dataInputStream,
            final DataOutputStream dataOutputStream) {
        this.socket = socket;
        this.dataInputStream = dataInputStream;
        this.dataOutputStream = dataOutputStream;
        new Thread(this::readDNSMessages).start();
    }

    private void readDNSMessages() {
        try {
            while (socket.isConnected()) {
                final DNSMessage response = readDNSMessage();
                final SettableFuture<DNSMessage> future;
                synchronized (inFlightQueries) {
                    future = inFlightQueries.remove(response.id);
                }
                if (future != null) {
                    future.set(response);
                } else {
                    Log.e(Config.LOGTAG, "no in flight query found for response id " + response.id);
                }
            }
            evictInFlightQueries(new EOFException());
        } catch (final IOException e) {
            evictInFlightQueries(e);
        }
    }

    private void evictInFlightQueries(final Exception e) {
        synchronized (inFlightQueries) {
            final Iterator<Map.Entry<Integer, SettableFuture<DNSMessage>>> iterator =
                    inFlightQueries.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<Integer, SettableFuture<DNSMessage>> entry = iterator.next();
                entry.getValue().setException(e);
                iterator.remove();
            }
        }
    }

    private static DNSSocket of(final Socket socket) throws IOException {
        final DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
        final DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
        return new DNSSocket(socket, dataInputStream, dataOutputStream);
    }

    public static DNSSocket connect(final DNSServer dnsServer) throws IOException {
        switch (dnsServer.uniqueTransport()) {
            case TCP:
                return connectTcpSocket(dnsServer);
            case TLS:
                return connectTlsSocket(dnsServer);
            default:
                throw new IllegalStateException("This is not a socket based transport");
        }
    }

    private static DNSSocket connectTcpSocket(final DNSServer dnsServer) throws IOException {
        Preconditions.checkArgument(dnsServer.uniqueTransport() == Transport.TCP);
        final SocketAddress socketAddress =
                new InetSocketAddress(dnsServer.inetAddress, dnsServer.port);
        final Socket socket = new Socket();
        socket.connect(socketAddress, CONNECT_TIMEOUT);
        socket.setSoTimeout(QUERY_TIMEOUT);
        return DNSSocket.of(socket);
    }

    private static DNSSocket connectTlsSocket(final DNSServer dnsServer) throws IOException {
        Preconditions.checkArgument(dnsServer.uniqueTransport() == Transport.TLS);
        final SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        final SSLSocket sslSocket;
        if (Strings.isNullOrEmpty(dnsServer.hostname)) {
            final SocketAddress socketAddress =
                    new InetSocketAddress(dnsServer.inetAddress, dnsServer.port);
            sslSocket = (SSLSocket) factory.createSocket(dnsServer.inetAddress, dnsServer.port);
            sslSocket.connect(socketAddress, CONNECT_TIMEOUT);
            sslSocket.setSoTimeout(QUERY_TIMEOUT);
        } else {
            sslSocket = (SSLSocket) factory.createSocket(dnsServer.hostname, dnsServer.port);
            sslSocket.setSoTimeout(QUERY_TIMEOUT);
            final SSLSession session = sslSocket.getSession();
            final Certificate[] peerCertificates = session.getPeerCertificates();
            if (peerCertificates.length == 0 || !(peerCertificates[0] instanceof X509Certificate)) {
                throw new IOException("Peer did not provide X509 certificates");
            }
            final X509Certificate certificate = (X509Certificate) peerCertificates[0];
            if (!OkHostnameVerifier.strictInstance().verify(dnsServer.hostname, certificate)) {
                throw new SSLPeerUnverifiedException("Peer did not provide valid certificates");
            }
        }
        return DNSSocket.of(sslSocket);
    }

    public DNSMessage query(final DNSMessage query) throws IOException, InterruptedException {
        try {
            return queryAsync(query).get(QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else {
                throw new IOException(e);
            }
        } catch (final TimeoutException e) {
            throw new IOException(e);
        }
    }

    public ListenableFuture<DNSMessage> queryAsync(final DNSMessage query)
            throws InterruptedException, IOException {
        final SettableFuture<DNSMessage> responseFuture = SettableFuture.create();
        synchronized (this.inFlightQueries) {
            this.inFlightQueries.put(query.id, responseFuture);
        }
        this.semaphore.acquire();
        try {
            query.writeTo(this.dataOutputStream);
            this.dataOutputStream.flush();
        } finally {
            this.semaphore.release();
        }
        return responseFuture;
    }

    private DNSMessage readDNSMessage() throws IOException {
        final int length = this.dataInputStream.readUnsignedShort();
        byte[] data = new byte[length];
        int read = 0;
        while (read < length) {
            read += this.dataInputStream.read(data, read, length - read);
        }
        return new DNSMessage(data);
    }

    @Override
    public void close() throws IOException {
        this.socket.close();
    }

    public void closeQuietly() {
        try {
            this.socket.close();
        } catch (final IOException ignored) {

        }
    }
}
