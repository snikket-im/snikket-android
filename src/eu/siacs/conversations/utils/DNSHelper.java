package eu.siacs.conversations.utils;

import de.measite.minidns.Client;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Record;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.Record.CLASS;
import de.measite.minidns.record.SRV;
import de.measite.minidns.record.Data;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.TreeMap;

import android.os.Bundle;
import android.util.Log;

public class DNSHelper {
	protected static Client client = new Client();

	public static Bundle getSRVRecord(String host) throws IOException {
		String dns[] = client.findDNS();

		if (dns != null) {
			// we have a list of DNS servers, let's go
			for (String dnsserver : dns) {
				InetAddress ip = InetAddress.getByName(dnsserver);
				Bundle b = queryDNS(host, ip);
				if (b.containsKey("name")) {
					return b;
				}
			}
		}

		// fallback
		return queryDNS(host, InetAddress.getByName("8.8.8.8"));
	}

	public static Bundle queryDNS(String host, InetAddress dnsServer) {
		Bundle namePort = new Bundle();
		try {
			Log.d("xmppService", "using dns server: " + dnsServer.getHostAddress()
					+ " to look up " + host);
			DNSMessage message =
					client.query(
							"_xmpp-client._tcp." + host,
							TYPE.SRV,
							CLASS.IN,
							dnsServer.getHostAddress());

			// How should we handle priorities and weight?
			// Wikipedia has a nice article about priorities vs. weights:
			// https://en.wikipedia.org/wiki/SRV_record#Provisioning_for_high_service_availability

			// we bucket the SRV records based on priority, pick per priority
			// a random order respecting the weight, and dump that priority by
			// priority

			TreeMap<Integer, ArrayList<SRV>> priorities =
					new TreeMap<Integer, ArrayList<SRV>>();

			for (Record rr : message.getAnswers()) {
				Data d = rr.getPayload();
				if (d instanceof SRV) {
					SRV srv = (SRV) d;
					if (!priorities.containsKey(srv.getPriority())) {
						priorities.put(srv.getPriority(), new ArrayList<SRV>(2));
					}
					priorities.get(srv.getPriority()).add(srv);
				}
			}

			Random rnd = new Random();
			ArrayList<SRV> result = new ArrayList<SRV>(priorities.size() * 2 + 1);
			for (ArrayList<SRV> s: priorities.values()) {

				// trivial case
				if (s.size() <= 1) {
					result.addAll(s);
					continue;
				}

				long totalweight = 0l;
				for (SRV srv: s) {
					totalweight += srv.getWeight();
				}

				while (totalweight > 0l && s.size() > 0) {
					long p = (rnd.nextLong() & 0x7fffffffffffffffl) % totalweight;
					int i = 0;
					while (p > 0) {
						p -= s.get(i++).getPriority();
					}
					i--;
					// remove is expensive, but we have only a few entries anyway
					SRV srv = s.remove(i);
					totalweight -= srv.getWeight();
					result.add(srv);
				}

				Collections.shuffle(s, rnd);
				result.addAll(s);

			}

			if (result.size() == 0) {
				namePort.putString("error", "nosrv");
				return namePort;
			}
			// we now have a list of servers to try :-)

			// classic name/port pair
			namePort.putString("name", result.get(0).getName());
			namePort.putInt("port", result.get(0).getPort());

			// add all other records
			int i = 0;
			for (SRV srv : result) {
				namePort.putString("name" + i, srv.getName());
				namePort.putInt("port" + i, srv.getPort());
				i++;
			}

		} catch (IOException e) {
			Log.e("xmppService", "io execpiton during dns", e);
			namePort.putString("error", "timeout");
		}
		return namePort;
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
