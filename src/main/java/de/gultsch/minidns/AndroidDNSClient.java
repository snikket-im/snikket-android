package de.gultsch.minidns;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import de.measite.minidns.AbstractDNSClient;
import de.measite.minidns.DNSMessage;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

public class AndroidDNSClient extends AbstractDNSClient {
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
    protected DNSMessage.Builder newQuestion(final DNSMessage.Builder message) {
        message.setRecursionDesired(true);
        message.getEdnsBuilder()
                .setUdpPayloadSize(networkDataSource.getUdpPayloadSize())
                .setDnssecOk(askForDnssec);
        return message;
    }

    @Override
    protected DNSMessage query(final DNSMessage.Builder queryBuilder) throws IOException {
        final DNSMessage question = newQuestion(queryBuilder).build();
        for (final DNSServer dnsServer : getDNSServers()) {
            final DNSMessage response = this.networkDataSource.query(question, dnsServer);
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

            return response;
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
        final ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            final Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                return new Network[] {activeNetwork};
            }
        }
        return connectivityManager.getAllNetworks();
    }
}
