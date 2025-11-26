package eu.siacs.conversations.utils;

import com.google.common.io.ByteStreams;
import com.google.common.net.InetAddresses;
import eu.siacs.conversations.Config;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class SocksSocketFactory {

    private static final byte[] LOCALHOST = new byte[] {127, 0, 0, 1};

    public static void createSocksConnection(
            final Socket socket, final String destination, final int port) throws IOException {
        final InputStream proxyIs = socket.getInputStream();
        final OutputStream proxyOs = socket.getOutputStream();
        proxyOs.write(new byte[] {0x05, 0x01, 0x00});
        proxyOs.flush();
        final byte[] handshake = new byte[2];
        ByteStreams.readFully(proxyIs, handshake);
        if (handshake[0] != 0x05 || handshake[1] != 0x00) {
            throw new SocksConnectionException("Socks 5 handshake failed");
        }
        final byte type;
        final ByteBuffer request;
        if (InetAddresses.isInetAddress(destination)) {
            final var ip = InetAddresses.forString(destination);
            final var dest = ip.getAddress();
            request = ByteBuffer.allocate(6 + dest.length);
            if (ip instanceof Inet4Address) {
                type = 0x01;
            } else if (ip instanceof Inet6Address) {
                type = 0x04;
            } else {
                throw new IOException("IP address is of unknown subtype");
            }
            request.put(new byte[] {0x05, 0x01, 0x00, type});
            request.put(dest);
        } else {
            final byte[] dest = destination.getBytes();
            type = 0x03;
            request = ByteBuffer.allocate(7 + dest.length);
            request.put(new byte[] {0x05, 0x01, 0x00, type});
            request.put((byte) dest.length);
            request.put(dest);
        }
        request.putShort((short) port);
        proxyOs.write(request.array());
        proxyOs.flush();
        final byte[] response = new byte[4];
        ByteStreams.readFully(proxyIs, response);
        final byte ver = response[0];
        if (ver != 0x05) {
            throw new IOException(String.format("Unknown Socks version %02X ", ver));
        }
        final byte status = response[1];
        final byte bndAddressType = response[3];
        final byte[] bndDestination = readDestination(bndAddressType, proxyIs);
        final byte[] bndPort = new byte[2];
        if (bndAddressType == 0x03) {
            final String receivedDestination = new String(bndDestination);
            if (!receivedDestination.equalsIgnoreCase(destination)) {
                throw new IOException(
                        String.format(
                                "Destination mismatch. Received %s Expected %s",
                                receivedDestination, destination));
            }
        }
        ByteStreams.readFully(proxyIs, bndPort);
        if (status != 0x00) {
            if (status == 0x04) {
                throw new HostNotFoundException("Host unreachable");
            }
            if (status == 0x05) {
                throw new HostNotFoundException("Connection refused");
            }
            throw new IOException(String.format("Unknown status code %02X ", status));
        }
    }

    private static byte[] readDestination(final byte type, final InputStream inputStream)
            throws IOException {
        final byte[] bndDestination;
        if (type == 0x01) {
            bndDestination = new byte[4];
        } else if (type == 0x03) {
            final int length = inputStream.read();
            bndDestination = new byte[length];
        } else if (type == 0x04) {
            bndDestination = new byte[16];
        } else {
            throw new IOException(String.format("Unknown Socks address type %02X ", type));
        }
        ByteStreams.readFully(inputStream, bndDestination);
        return bndDestination;
    }

    public static boolean contains(byte needle, byte[] haystack) {
        for (byte hay : haystack) {
            if (hay == needle) {
                return true;
            }
        }
        return false;
    }

    private static Socket createSocket(InetSocketAddress address, String destination, int port)
            throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(address, Config.CONNECT_TIMEOUT * 1000);
        } catch (IOException e) {
            throw new SocksProxyNotFoundException();
        }
        createSocksConnection(socket, destination, port);
        return socket;
    }

    public static Socket createSocketOverTor(String destination, int port) throws IOException {
        return createSocket(
                new InetSocketAddress(InetAddress.getByAddress(LOCALHOST), 9050),
                destination,
                port);
    }

    private static class SocksConnectionException extends IOException {
        SocksConnectionException(String message) {
            super(message);
        }
    }

    public static class SocksProxyNotFoundException extends IOException {}

    public static class HostNotFoundException extends SocksConnectionException {
        HostNotFoundException(String message) {
            super(message);
        }
    }
}
