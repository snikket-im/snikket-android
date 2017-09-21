package eu.siacs.conversations.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSName;
import de.measite.minidns.dnssec.DNSSECResultNotAuthenticException;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;
import de.measite.minidns.hla.DnssecResolverApi;
import de.measite.minidns.hla.ResolverApi;
import de.measite.minidns.hla.ResolverResult;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.CNAME;
import de.measite.minidns.record.Data;
import de.measite.minidns.record.InternetAddressRR;
import de.measite.minidns.record.SRV;
import de.measite.minidns.util.MultipleIoException;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;

public class Resolver {

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERICE = "_xmpp-client";

    private static final String NETWORK_IS_UNREACHABLE = "Network is unreachable";

    private static XmppConnectionService SERVICE = null;


    public static void init(XmppConnectionService service) {
        Resolver.SERVICE = service;
        DNSClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(service));
    }

    public static List<Result> resolve(String domain) throws NetworkIsUnreachableException {
        List<Result> results = new ArrayList<>();
        HashSet<String> messages = new HashSet<>();
        try {
            results.addAll(resolveSrv(domain, true));
        } catch (MultipleIoException e) {
            messages.addAll(extractMessages(e));
        } catch (Throwable throwable) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": error resolving SRV record (direct TLS)",throwable);
        }
        try {
            results.addAll(resolveSrv(domain, false));
        } catch (MultipleIoException e) {
            messages.addAll(extractMessages(e));
        } catch (Throwable throwable) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": error resolving SRV record (STARTTLS)",throwable);
        }
        if (results.size() == 0) {
            if (messages.size() == 1 && messages.contains(NETWORK_IS_UNREACHABLE)) {
                throw new NetworkIsUnreachableException();
            }
            results.addAll(resolveNoSrvRecords(DNSName.from(domain),true));
        }
        Collections.sort(results);
        Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+results.toString());
        return results;
    }

    private static HashSet<String> extractMessages(MultipleIoException e) {
        HashSet<String> messages = new HashSet<>();
        for(Exception inner : e.getExceptions()) {
            if (inner instanceof MultipleIoException) {
                messages.addAll(extractMessages((MultipleIoException) inner));
            } else {
                messages.add(inner.getMessage());
            }
        }
        return messages;
    }

    private static List<Result> resolveSrv(String domain, final boolean directTls) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            return Collections.emptyList();
        }
        DNSName dnsName = DNSName.from((directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERICE)+"._tcp."+domain);
        ResolverResult<SRV> result = resolveWithFallback(dnsName,SRV.class);
        List<Result> results = new ArrayList<>();
        for(SRV record : result.getAnswersOrEmptySet()) {
            final boolean addedIPv4 = results.addAll(resolveIp(record,A.class,result.isAuthenticData(),directTls));
            results.addAll(resolveIp(record,AAAA.class,result.isAuthenticData(),directTls));
            if (!addedIPv4 && !Thread.currentThread().isInterrupted()) {
                Result resolverResult = Result.fromRecord(record, directTls);
                resolverResult.authenticated = resolverResult.isAuthenticated();
                results.add(resolverResult);
            }
        }
        return results;
    }

    private static <D extends InternetAddressRR> List<Result> resolveIp(SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        if (Thread.currentThread().isInterrupted()) {
            return Collections.emptyList();
        }
        List<Result> list = new ArrayList<>();
        try {
            ResolverResult<D> results = resolveWithFallback(srv.name,type, authenticated);
            for (D record : results.getAnswersOrEmptySet()) {
                Result resolverResult = Result.fromRecord(srv, directTls);
                resolverResult.authenticated = results.isAuthenticData() && authenticated;
                resolverResult.ip = record.getInetAddress();
                list.add(resolverResult);
            }
        } catch (Throwable t) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": error resolving "+type.getSimpleName()+" "+t.getMessage());
        }
        return list;
    }

    private static List<Result> resolveNoSrvRecords(DNSName dnsName, boolean withCnames) {
        List<Result> results = new ArrayList<>();
        try {
            for(A a : resolveWithFallback(dnsName,A.class,false).getAnswersOrEmptySet()) {
                results.add(Result.createDefault(dnsName,a.getInetAddress()));
            }
            for(AAAA aaaa : resolveWithFallback(dnsName,AAAA.class,false).getAnswersOrEmptySet()) {
                results.add(Result.createDefault(dnsName,aaaa.getInetAddress()));
            }
            if (results.size() == 0 && withCnames) {
                for (CNAME cname : resolveWithFallback(dnsName, CNAME.class, false).getAnswersOrEmptySet()) {
                    results.addAll(resolveNoSrvRecords(cname.name, false));
                }
            }
        } catch (Throwable throwable) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + "error resolving fallback records",throwable);
        }
        results.add(Result.createDefault(dnsName));
        return results;
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(DNSName dnsName, Class<D> type) throws IOException {
        return resolveWithFallback(dnsName,type,validateHostname());
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(DNSName dnsName, Class<D> type, boolean validateHostname) throws IOException {
        if (!validateHostname) {
            return ResolverApi.INSTANCE.resolve(dnsName, type);
        }
        try {
            final ResolverResult<D> r = DnssecResolverApi.INSTANCE.resolveDnssecReliable(dnsName, type);
            if (r.wasSuccessful()) {
                if (r.getAnswers().isEmpty() && type.equals(SRV.class)) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": resolving  SRV records of " + dnsName.toString() + " with DNSSEC yielded empty result");
                }
                return r;
            }
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", r.getResolutionUnsuccessfulException());
        } catch (DNSSECResultNotAuthenticException e) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", e);
        } catch (IOException e) {
            throw e;
        } catch (Throwable throwable) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", throwable);
        }
        return ResolverApi.INSTANCE.resolve(dnsName, type);
    }

    private static boolean validateHostname() {
        return SERVICE != null && SERVICE.getBooleanPreference("validate_hostname", R.bool.validate_hostname);
    }

    public static class Result implements Comparable<Result> {
        private InetAddress ip;
        private DNSName hostname;
        private int port = 5222;
        private boolean directTls = false;
        private boolean authenticated =false;
        private int priority;

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public DNSName getHostname() {
            return hostname;
        }

        public boolean isDirectTls() {
            return directTls;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "ip='" + (ip==null?null:ip.getHostAddress()) + '\'' +
                    ", hostame='" + hostname.toString() + '\'' +
                    ", port=" + port +
                    ", directTls=" + directTls +
                    ", authenticated=" + authenticated +
                    ", priority=" + priority +
                    '}';
        }

        @Override
        public int compareTo(@NonNull Result result) {
            if (result.priority == priority) {
                if (directTls == result.directTls) {
                    if (ip == null && result.ip == null) {
                        return 0;
                    } else if (ip != null && result.ip != null) {
                        if (ip instanceof Inet4Address && result.ip instanceof Inet4Address) {
                            return 0;
                        } else {
                            return ip instanceof Inet4Address ? -1 : 1;
                        }
                    } else {
                        return ip != null ? -1 : 1;
                    }
                } else {
                    return directTls ? -1 : 1;
                }
            } else {
                return priority - result.priority;
            }
        }

        public static Result fromRecord(SRV srv, boolean directTls) {
            Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.name;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        public static Result createDefault(DNSName hostname, InetAddress ip) {
            Result result = new Result();
            result.port = 5222;
            result.hostname = hostname;
            result.ip = ip;
            return result;
        }

        public static Result createDefault(DNSName hostname) {
            return createDefault(hostname,null);
        }
    }
    public static class NetworkIsUnreachableException extends Exception {

    }

}
