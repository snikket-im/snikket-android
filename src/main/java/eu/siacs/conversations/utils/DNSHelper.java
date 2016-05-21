package eu.siacs.conversations.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.Map;
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

	protected static Context context;

	public static Bundle getSRVRecord(final Jid jid, Context context) throws IOException {
		DNSHelper.context = context;
        final String host = jid.getDomainpart();
		final List<InetAddress> servers = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? getDnsServers(context) : getDnsServersPreLollipop();
		Bundle b = new Bundle();
		boolean interrupted = false;
		for(InetAddress server : servers) {
			if (Thread.currentThread().isInterrupted()) {
				interrupted = true;
				break;
			}
			b = queryDNS(host, server);
			if (b.containsKey("values")) {
				return b;
			}
		}
		if (!b.containsKey("values")) {
			Log.d(Config.LOGTAG,(interrupted ? "Thread interrupted during DNS query" :"all dns queries failed") + ". provide fallback A record");
			ArrayList<Parcelable> values = new ArrayList<>();
			values.add(createNamePortBundle(host, 5222, false));
			b.putParcelableArrayList("values",values);
		}
		return b;
	}

	@TargetApi(21)
	private static List<InetAddress> getDnsServers(Context context) {
		List<InetAddress> servers = new ArrayList<>();
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		Network[] networks = connectivityManager == null ? null : connectivityManager.getAllNetworks();
		if (networks == null) {
			return getDnsServersPreLollipop();
		}
		for(int i = 0; i < networks.length; ++i) {
			LinkProperties linkProperties = connectivityManager.getLinkProperties(networks[i]);
			if (linkProperties != null) {
				if (hasDefaultRoute(linkProperties)) {
					servers.addAll(0, getIPv4First(linkProperties.getDnsServers()));
				} else {
					servers.addAll(getIPv4First(linkProperties.getDnsServers()));
				}
			}
		}
		if (servers.size() > 0) {
			Log.d(Config.LOGTAG, "used lollipop variant to discover dns servers in " + networks.length + " networks");
		}
		return servers.size() > 0 ? servers : getDnsServersPreLollipop();
	}

	private static List<InetAddress> getIPv4First(List<InetAddress> in) {
		List<InetAddress> out = new ArrayList<>();
		for(InetAddress addr : in) {
			if (addr instanceof Inet4Address) {
				out.add(0, addr);
			} else {
				out.add(addr);
			}
		}
		return out;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private static boolean hasDefaultRoute(LinkProperties linkProperties) {
		for(RouteInfo route: linkProperties.getRoutes()) {
			if (route.isDefaultRoute()) {
				return true;
			}
		}
		return false;
	}

	private static List<InetAddress> getDnsServersPreLollipop() {
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

	private static class TlsSrv {
		private final SRV srv;
		private final boolean tls;

		public TlsSrv(SRV srv, boolean tls) {
			this.srv = srv;
			this.tls = tls;
		}
	}

	private static void fillSrvMaps(final String qname, final InetAddress dnsServer, final Map<Integer, List<TlsSrv>> priorities, final Map<String, List<String>> ips4, final Map<String, List<String>> ips6, final boolean tls) throws IOException {
		final DNSMessage message = client.query(qname, TYPE.SRV, CLASS.IN, dnsServer.getHostAddress());
		for (Record[] rrset : new Record[][] { message.getAnswers(), message.getAdditionalResourceRecords() }) {
			for (Record rr : rrset) {
				Data d = rr.getPayload();
				if (d instanceof SRV && NameUtil.idnEquals(qname, rr.getName())) {
					SRV srv = (SRV) d;
					if (!priorities.containsKey(srv.getPriority())) {
						priorities.put(srv.getPriority(),new ArrayList<TlsSrv>());
					}
					priorities.get(srv.getPriority()).add(new TlsSrv(srv, tls));
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
	}

	public static Bundle queryDNS(String host, InetAddress dnsServer) {
		Bundle bundle = new Bundle();
		try {
			client.setTimeout(Config.SOCKET_TIMEOUT * 1000);
			final String qname = "_xmpp-client._tcp." + host;
			final String tlsQname = "_xmpps-client._tcp." + host;
			Log.d(Config.LOGTAG, "using dns server: " + dnsServer.getHostAddress() + " to look up " + host);

			final Map<Integer, List<TlsSrv>> priorities = new TreeMap<>();
			final Map<String, List<String>> ips4 = new TreeMap<>();
			final Map<String, List<String>> ips6 = new TreeMap<>();

			fillSrvMaps(qname, dnsServer, priorities, ips4, ips6, false);
			fillSrvMaps(tlsQname, dnsServer, priorities, ips4, ips6, true);

			final List<TlsSrv> result = new ArrayList<>();
			for (final List<TlsSrv> s : priorities.values()) {
				result.addAll(s);
			}

			final ArrayList<Bundle> values = new ArrayList<>();
			if (result.size() == 0) {
				DNSMessage response;
				try {
					response = client.query(host, TYPE.A, CLASS.IN, dnsServer.getHostAddress());
					for (int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload(), false));
					}
				} catch (SocketTimeoutException e) {
					Log.d(Config.LOGTAG,"ignoring timeout exception when querying A record on "+dnsServer.getHostAddress());
				}
				try {
					response = client.query(host, TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
					for (int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(host, 5222, response.getAnswers()[i].getPayload(), false));
					}
				} catch (SocketTimeoutException e) {
					Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
				}
				values.add(createNamePortBundle(host, 5222, false));
				bundle.putParcelableArrayList("values", values);
				return bundle;
			}
			for (final TlsSrv tlsSrv : result) {
				final SRV srv = tlsSrv.srv;
				if (ips6.containsKey(srv.getName())) {
					values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips6, tlsSrv.tls));
				} else {
					try {
						DNSMessage response = client.query(srv.getName(), TYPE.AAAA, CLASS.IN, dnsServer.getHostAddress());
						for (int i = 0; i < response.getAnswers().length; ++i) {
							values.add(createNamePortBundle(srv.getName(), srv.getPort(), response.getAnswers()[i].getPayload(), tlsSrv.tls));
						}
					} catch (SocketTimeoutException e) {
						Log.d(Config.LOGTAG,"ignoring timeout exception when querying AAAA record on "+dnsServer.getHostAddress());
					}
				}
				if (ips4.containsKey(srv.getName())) {
					values.add(createNamePortBundle(srv.getName(),srv.getPort(),ips4, tlsSrv.tls));
				} else {
					DNSMessage response = client.query(srv.getName(), TYPE.A, CLASS.IN, dnsServer.getHostAddress());
					for(int i = 0; i < response.getAnswers().length; ++i) {
						values.add(createNamePortBundle(srv.getName(),srv.getPort(),response.getAnswers()[i].getPayload(), tlsSrv.tls));
					}
				}
				values.add(createNamePortBundle(srv.getName(), srv.getPort(), tlsSrv.tls));
			}
			bundle.putParcelableArrayList("values", values);
		} catch (SocketTimeoutException e) {
			bundle.putString("error", "timeout");
		} catch (Exception e) {
			bundle.putString("error", "unhandled");
		}
		return bundle;
	}

	private static Bundle createNamePortBundle(String name, int port, final boolean tls) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putBoolean("tls", tls);
		namePort.putInt("port", port);
		return namePort;
	}

	private static Bundle createNamePortBundle(String name, int port, Map<String, List<String>> ips, final boolean tls) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putBoolean("tls", tls);
		namePort.putInt("port", port);
		if (ips!=null) {
			List<String> ip = ips.get(name);
			Collections.shuffle(ip, new Random());
			namePort.putString("ip", ip.get(0));
		}
		return namePort;
	}

	private static Bundle createNamePortBundle(String name, int port, Data data, final boolean tls) {
		Bundle namePort = new Bundle();
		namePort.putString("name", name);
		namePort.putBoolean("tls", tls);
		namePort.putInt("port", port);
		if (data instanceof A) {
			namePort.putString("ip", data.toString());
		} else if (data instanceof AAAA) {
			namePort.putString("ip","["+data.toString()+"]");
		}
		return namePort;
	}

	public static boolean isIp(final String server) {
		return server != null && (
				PATTERN_IPV4.matcher(server).matches()
				|| PATTERN_IPV6.matcher(server).matches()
				|| PATTERN_IPV6_6HEX4DEC.matcher(server).matches()
				|| PATTERN_IPV6_HEX4DECCOMPRESSED.matcher(server).matches()
				|| PATTERN_IPV6_HEXCOMPRESSED.matcher(server).matches());
	}
}
