package eu.siacs.conversations.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Random;

import android.os.Bundle;
import android.util.Log;

public class DNSHelper {
	public static Bundle getSRVRecord(String host) throws IOException {
		InetAddress ip = InetAddress.getByName("8.8.8.8");
		try {
			Class<?> SystemProperties = Class
					.forName("android.os.SystemProperties");
			Method method = SystemProperties.getMethod("get",
					new Class[] { String.class });
			ArrayList<String> servers = new ArrayList<String>();
			for (String name : new String[] { "net.dns1", "net.dns2",
					"net.dns3", "net.dns4", }) {
				String value = (String) method.invoke(null, name);

				if (value != null && !"".equals(value)
						&& !servers.contains(value)) {
					ip = InetAddress.getByName(value);
					servers.add(value);
					Bundle result = queryDNS(host, ip);
					if (!result.containsKey("error")) {
						return result;
					}
				}
			}
		} catch (Exception e) {
			Log.d("xmppService","error during system calls");
		}
		ip = InetAddress.getByName("8.8.8.8");
		return queryDNS(host, ip);
	}

	public static Bundle queryDNS(String host, InetAddress dnsServer) {
		Bundle namePort = new Bundle();
		try {
			Log.d("xmppService", "using dns server: " + dnsServer.toString()
					+ " to look up " + host);
			String[] hostParts = host.split("\\.");
			byte[] transId = new byte[2];
			Random random = new Random();
			random.nextBytes(transId);
			byte[] header = { 0x01, 0x20, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
					0x00, 0x01, 0x0c, 0x5f, 0x78, 0x6d, 0x70, 0x70, 0x2d, 0x63,
					0x6c, 0x69, 0x65, 0x6e, 0x74, 0x04, 0x5f, 0x74, 0x63, 0x70 };
			byte[] rest = { 0x00, 0x00, 0x21, 0x00, 0x01, 0x00, 0x00, 0x29,
					0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			output.write(transId);
			output.write(header);
			for (int i = 0; i < hostParts.length; ++i) {
				char[] tmpChars = hostParts[i].toCharArray();
				byte[] tmp = new byte[tmpChars.length];
				for (int j = 0; j < tmpChars.length; ++j) {
					tmp[j] = (byte) tmpChars[j];
				}
				output.write(tmp.length);
				output.write(tmp);
			}
			output.write(rest);
			byte[] sendPaket = output.toByteArray();
			int realLenght = sendPaket.length - 11;
			DatagramPacket packet = new DatagramPacket(sendPaket,
					sendPaket.length, dnsServer, 53);
			DatagramSocket datagramSocket = new DatagramSocket();
			datagramSocket.send(packet);
			byte[] receiveData = new byte[1024];

			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			datagramSocket.setSoTimeout(3000);
			datagramSocket.receive(receivePacket);
			if (receiveData[3] != -128) {
				namePort.putString("error", "nosrv");
				return namePort;
			}
			namePort.putInt(
					"port",
					calcPort(receiveData[realLenght + 16],
							receiveData[realLenght + 17]));
			int i = realLenght + 18;
			int wordLenght = 0;
			StringBuilder builder = new StringBuilder();
			while (receiveData[i] != 0) {
				if (wordLenght > 0) {
					builder.append((char) receiveData[i]);
					--wordLenght;
				} else {
					wordLenght = receiveData[i];
					builder.append(".");
				}
				++i;
			}
			builder.replace(0, 1, "");
			byte type = receiveData[i + 1];
			byte type2 = receiveData[i + 2];
			if ((type == -64) || (type == type2)) {
				namePort.putString("name", builder.toString());
				return namePort;
			} else {
				Log.d("xmppService", "type=" + type + " type2=" + type2 + " "
						+ builder.toString());
				namePort.putString("error", "nosrv");
				return namePort;
			}
		} catch (IOException e) {
			Log.d("xmppService", "io execpiton during dns");
			namePort.putString("error", "nosrv");
			return namePort;
		}
	}

	static int calcPort(byte hb, byte lb) {
		int port = ((int) hb << 8) | ((int) lb & 0xFF);
		if (port >= 0) {
			return port;
		} else {
			return 65536 + port;
		}
	}

	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
