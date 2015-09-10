package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.regex.Pattern;

import de.measite.minidns.Client;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.Record;
import de.measite.minidns.Record.CLASS;
import de.measite.minidns.Record.TYPE;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.Data;
import de.measite.minidns.record.SRV;
import de.measite.minidns.util.NameUtil;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.jid.Jid;

public class DNSHelper {

	public static final Pattern PATTERN_IPV4 = Pattern.compile("\\A(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
	public static final Pattern PATTERN_IPV6_HEX4DECCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?) ::((?:[0-9A-Fa-f]{1,4}:)*)(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
	public static final Pattern PATTERN_IPV6_6HEX4DEC = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}:){6,6})(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}\\z");
	public static final Pattern PATTERN_IPV6_HEXCOMPRESSED = Pattern.compile("\\A((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)::((?:[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4})*)?)\\z");
	public static final Pattern PATTERN_IPV6 = Pattern.compile("\\A(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\z");

	protected static Client client = new Client();

	public static Bundle getSRVRecord(final Jid jid, Context context) throws IOException {
        final String host = jid.getDomainpart();
		final List<InetAddress> servers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? getDnsServers(context) : getDnsServersPreLolipop();
		Bundle b = null;
		for(InetAddress server : servers) {
			b = queryDNS(host, server);
			if (b.containsKey("values")) {
				return b;
			}
		}
		return b;
	}

	@TargetApi(21)
	private static List<InetAddress> getDnsServers(Context context) {
		List<InetAddress> servers = new ArrayList<>();
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		Network[] networks = connectivityManager.getAllNetworks();
		for(int i = 0; i < networks.length; ++i) {
			LinkProperties linkProperties = connectivityManager.getLinkProperties(networks[i]);
			servers.addAll(linkProperties.getDnsServers());
		}
		return servers.size() > 0 ? servers : getDnsServersPreLolipop();
	}

	private static List<InetAddress> getDnsServersPreLolipop() {
		List<InetAddress> servers = new ArrayList<>();
		String[] dns = client.findDNS();
		for(int i = 0; i < dns.length; ++i) {
			try {
				servers.add(InetAddress.getByName(dns[i]));
			} catch (UnknownHostException e) {
				//ignore
			}
		}
		return servers;
	}

	public static Bundle queryDNS(String host, InetAddress dnsServer) {
		Bundle bundle = new Bundle();
		try {
			client.setTimeout(Config.PING_TIMEOUT * 1000);
			String qname = "_xmpp-client._tcp." + host;
			Log.d(Config.LOGTAG, "using dns server: " + dnsServer.getHostAddress() + " to look up " + host);
			DNSMessage message = client.query(qname, TYPE.SRV, CLASS.IN, dnsServer.getHostAddress());

			TreeMap<Integer, ArrayList<SRV>> priorities = new TreeMap<>();
			TreeMap<String, ArrayList<String>> ips4 = new TreeMap<>();
			TreeMap<String, ArrayList<String>> ips6 = new TreeMap<>();

			for (Record[] rrset : new Record[][] { message.getAnswers(), message.getAdditionalResourceRecords() }) {
				for (Record rr : rrset) {
					Data d = rr.getPayload();
					if (d instanceof SRV && NameUtil.idnEquals(qname, rr.getName())) {
						SRV srv = (SRV) d;
						if (!priorities.containsKey(srv.getPriority())) {
							priorities.put(srv.getPriority(),new ArrayList<SRV>());
						}
						priorities.get(srv.getPriority()).add(srv);
					}
					if (d instanceof A) {
						A a = (A) d;
						if (!ips4.containsKey(rr.getName())) {
							ips4.put(rr.getName(), new ArrayList<String>());
						}
						ips4.get(rr.getName()).add(a.toString());
					}
					if (d instanceof AAAA) {
						AAAA aaaa = (AAAA) d;
						if (!ips6.containsKey(rr.getName())) {
							ips6.put(rr.getName(), new ArrayList<String>());
						}
						ips6.get(rr.getName()).add("[" + aaaa.toString() + "]");
					}
				}
			}

			ArrayList<SRV> result = new ArrayList<>();
			for (ArrayList<SRV> s : priorities.values()) {
				result.addAll(s);
			}

			ArrayList<Bundle> values = new ArrayList<>();
			if (result.size() == 0) {
				DNSMessage response;
				try {
					response = client.query(host, TYPE.A, CLASS.IN, dnsServer.getHostAddress());
					for (int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload()));
					}
				} catch (SocketTimeoutException e) {
					Log.d(Config.LOGTAG,"ignoring timeout exception when querying A record on "+dnsServer.getHostAddress());
				}
				try {
					response = client.query(host, TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
					for (int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload()));
					}
				} catch (SocketTimeoutException e) {
					Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
				}
				values.add(createNamePortBundle(host,5222));
				bundle.putParcelableArrayList("values", values);
				return bundle;
			}
			for (SRV srv : result) {
				if (ips6.containsKey(srv.getName())) {
					values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips6));
				} else {
					try {
						DNSMessage response = client.query(srv.getName(), TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
						for (int i = 0; i < response.getAnswers().length; ++i) {
							values.add(createNamePortBundle(srv.getName(), srv.getPort(), response.getAnswers()[i].getPayload()));
						}
					} catch (SocketTimeoutException e) {
						Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
					}
				}
				if (ips4.containsKey(srv.getName())) {
					values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips4));
				} else {
					DNSMessage response = client.query(srv.getName(), TYPE.A, CLASS.IN, dnsServer.getHostAddress());
					for(int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(srv.getName(),srv.getPort(),response.getAnswers()[i].getPayload()));
					}
				}
				values.add(createNamePortBundle(srv.getName(), srv.getPort()));
			}
			bundle.putParcelableArrayList("values", values);
		} catch (SocketTimeoutException e) {
			bundle.putString("error", "timeout");
		} catch (Exception e) {
			bundle.putString("error", "unhandled");
		}
		return bundle;
	}

	private static Bundle createNamePortBundle(String name, int port) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putInt("port", port);
		return namePort;
	}

	private static Bundle createNamePortBundle(String name, int port, TreeMap<String, ArrayList<String>> ips) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putInt("port", port);
		if (ips!=null) {
			ArrayList<String> ip = ips.get(name);
			Collections.shuffle(ip, new Random());
			namePort.putString("ip", ip.get(0));
		}
		return namePort;
	}

	private static Bundle createNamePortBundle(String name, int port, Data data) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putInt("port", port);
		if (data instanceof A) {
			namePort.putString("ip", data.toString());
		} else if (data instanceof AAAA) {
			namePort.putString("ip","["+data.toString()+"]");
		}
		return namePort;
	}

	public static boolean isIp(final String server) {
		return PATTERN_IPV4.matcher(server).matches()
				|| PATTERN_IPV6.matcher(server).matches()
				|| PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
				|| PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
				|| PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches();
	}
}
