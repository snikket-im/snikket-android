package de.gultsch.minidns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.util.Log;

import androidx.collection.LruCache;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

import org.minidns.AbstractDnsClient;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsqueryresult.DirectCachedDnsQueryResult;
import org.minidns.dnsqueryresult.DnsQueryResult;
import org.minidns.dnsqueryresult.StandardDnsQueryResult;
import org.minidns.dnsqueryresult.SynthesizedCachedDnsQueryResult;
import org.minidns.record.Data;

import org.minidns.record.Record;

import eu.siacs.conversations.Config;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

public class AndroidDNSClient extends AbstractDnsClient {

    private static final long DNS_MAX_TTL = 86_400L;

    private static final LruCache<QuestionServerTuple, DnsMessage> QUERY_CACHE =
            new LruCache<>(1024);
    private final Context context;
    private final NetworkDataSource networkDataSource = new NetworkDataSource();
    private boolean askForDnssec = false;

    public AndroidDNSClient(final Context context) {
        super();
        this.setDataSource(networkDataSource);
        this.context = context;
    }

    private static String getPrivateDnsServerName(final LinkProperties linkProperties) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return linkProperties.getPrivateDnsServerName();
        } else {
            return null;
        }
    }

    private static boolean isPrivateDnsActive(final LinkProperties linkProperties) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return linkProperties.isPrivateDnsActive();
        } else {
            return false;
        }
    }

    @Override
    protected DnsMessage.Builder newQuestion(final DnsMessage.Builder message) {
        message.setRecursionDesired(true);
        message.getEdnsBuilder()
                .setUdpPayloadSize(networkDataSource.getUdpPayloadSize())
                .setDnssecOk(askForDnssec);
        return message;
    }

    @Override
    protected DnsQueryResult query(final DnsMessage.Builder queryBuilder) throws IOException {
        final DnsMessage question = newQuestion(queryBuilder).build();
        for (final DNSServer dnsServer : getDNSServers()) {
            final QuestionServerTuple cacheKey = new QuestionServerTuple(dnsServer, question);
            final DnsMessage cachedResponse = queryCache(cacheKey);
            if (cachedResponse != null) {
                return new CachedDnsQueryResult(question, cachedResponse);
            }
            final DnsQueryResult result = this.networkDataSource.query(question, dnsServer);
            final var response = result.response;
            if (response == null) {
                continue;
            }
            switch (response.responseCode) {
                case NO_ERROR:
                case NX_DOMAIN:
                    break;
                default:
                    continue;
            }
            cacheQuery(cacheKey, response);
            return new StandardDnsQueryResult(dnsServer.inetAddress, dnsServer.port,result.queryMethod,question,response);
        }
        return null;
    }

    public boolean isAskForDnssec() {
        return askForDnssec;
    }

    public void setAskForDnssec(boolean askForDnssec) {
        this.askForDnssec = askForDnssec;
    }

    private List<DNSServer> getDNSServers() {
        final ImmutableList.Builder<DNSServer> dnsServerBuilder = new ImmutableList.Builder<>();
        final ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        final Network[] networks = getActiveNetworks(connectivityManager);
        for (final Network network : networks) {
            final LinkProperties linkProperties = connectivityManager.getLinkProperties(network);
            if (linkProperties == null) {
                continue;
            }
            final String privateDnsServerName = getPrivateDnsServerName(linkProperties);
            if (Strings.isNullOrEmpty(privateDnsServerName)) {
                final boolean isPrivateDns = isPrivateDnsActive(linkProperties);
                for (final InetAddress dnsServer : linkProperties.getDnsServers()) {
                    if (isPrivateDns) {
                        dnsServerBuilder.add(new DNSServer(dnsServer, Transport.TLS));
                    } else {
                        dnsServerBuilder.add(new DNSServer(dnsServer));
                    }
                }
            } else {
                dnsServerBuilder.add(new DNSServer(privateDnsServerName, Transport.TLS));
            }
        }
        return dnsServerBuilder.build();
    }

    private Network[] getActiveNetworks(final ConnectivityManager connectivityManager) {
        if (connectivityManager == null) {
            return new Network[0];
        }
        final Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork != null) {
                return new Network[] {activeNetwork};
        }
        return connectivityManager.getAllNetworks();
    }

    private DnsMessage queryCache(final QuestionServerTuple key) {
        final DnsMessage cachedResponse;
        synchronized (QUERY_CACHE) {
            cachedResponse = QUERY_CACHE.get(key);
            if (cachedResponse == null) {
                return null;
            }
            final long expiresIn = expiresIn(cachedResponse);
            if (expiresIn < 0) {
                QUERY_CACHE.remove(key);
                return null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(
                        Config.LOGTAG,
                        "DNS query came from cache. expires in " + Duration.ofMillis(expiresIn));
            }
        }
        return cachedResponse;
    }

    private void cacheQuery(final QuestionServerTuple key, final DnsMessage response) {
        if (response.receiveTimestamp <= 0) {
            return;
        }
        synchronized (QUERY_CACHE) {
            QUERY_CACHE.put(key, response);
        }
    }

    private static long ttl(final DnsMessage dnsMessage) {
        final List<Record<? extends Data>> answerSection = dnsMessage.answerSection;
        if (answerSection == null || answerSection.isEmpty()) {
            final List<Record<? extends Data>> authoritySection = dnsMessage.authoritySection;
            if (authoritySection == null || authoritySection.isEmpty()) {
                return 0;
            } else {
                return Collections.min(Collections2.transform(authoritySection, d -> d.ttl));
            }

        } else {
            return Collections.min(Collections2.transform(answerSection, d -> d.ttl));
        }
    }

    private static long expiresAt(final DnsMessage dnsMessage) {
        return dnsMessage.receiveTimestamp + (Math.min(DNS_MAX_TTL, ttl(dnsMessage)) * 1000L);
    }

    private static long expiresIn(final DnsMessage dnsMessage) {
        return expiresAt(dnsMessage) - System.currentTimeMillis();
    }

    private static class QuestionServerTuple {
        private final DNSServer dnsServer;
        private final DnsMessage question;

        private QuestionServerTuple(final DNSServer dnsServer, final DnsMessage question) {
            this.dnsServer = dnsServer;
            this.question = question.asNormalizedVersion();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QuestionServerTuple that = (QuestionServerTuple) o;
            return Objects.equal(dnsServer, that.dnsServer)
                    && Objects.equal(question, that.question);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(dnsServer, question);
        }
    }

    public static class CachedDnsQueryResult extends DnsQueryResult {

        private CachedDnsQueryResult(final DnsMessage query, final DnsMessage response) {
            super(QueryMethod.cachedDirect, query, response);
        }
    }
}
