package eu.siacs.conversations.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

import eu.siacs.conversations.Config;

public class SocksSocketFactory {

	private static final byte[] LOCALHOST = new byte[]{127,0,0,1};

	public static void createSocksConnection(Socket socket, String destination, int port) throws IOException {
		final InputStream proxyIs = socket.getInputStream();
		final OutputStream proxyOs = socket.getOutputStream();
		proxyOs.write(new byte[]{0x05, 0x01, 0x00});
		byte[] response = new byte[2];
		proxyIs.read(response);
		if (response[0] != 0x05 || response[1] != 0x00) {
			throw new SocksConnectionException("Socks 5 handshake failed");
		}
		byte[] dest = destination.getBytes();
		final ByteBuffer request = ByteBuffer.allocate(7 + dest.length);
		request.put(new byte[]{0x05, 0x01, 0x00, 0x03});
		request.put((byte) dest.length);
		request.put(dest);
		request.putShort((short) port);
		proxyOs.write(request.array());
		response = new byte[7 + dest.length];
		proxyIs.read(response);
		if (response[1] != 0x00) {
			if (response[1] == 0x04) {
				throw new HostNotFoundException("Host unreachable");
			}
			if (response[1] == 0x05) {
				throw new HostNotFoundException("Connection refused");
			}
			throw new SocksConnectionException("Unable to connect to destination "+(int) (response[1]));
		}
	}

	public static boolean contains(byte needle, byte[] haystack) {
		for(byte hay : haystack) {
			if (hay == needle) {
				return true;
			}
		}
		return false;
	}

	public static Socket createSocket(InetSocketAddress address, String destination, int port) throws IOException {
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
		return createSocket(new InetSocketAddress(InetAddress.getByAddress(LOCALHOST), 9050), destination, port);
	}

	private static class SocksConnectionException extends IOException {
		SocksConnectionException(String message) {
			super(message);
		}
	}

	public static class SocksProxyNotFoundException extends IOException {

	}

	public static class HostNotFoundException extends SocksConnectionException {
		HostNotFoundException(String message) {
			super(message);
		}
	}
}
