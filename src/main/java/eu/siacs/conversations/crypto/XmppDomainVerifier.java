package eu.siacs.conversations.crypto;

import android.util.Log;
import android.util.Pair;
import androidx.annotation.NonNull;
import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import eu.siacs.conversations.utils.IP;
import java.io.IOException;
import java.net.IDN;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

public class XmppDomainVerifier {

    private static final String LOGTAG = "XmppDomainVerifier";

    private static final String SRV_NAME = "1.3.6.1.5.5.7.8.7";
    private static final String XMPP_ADDR = "1.3.6.1.5.5.7.8.5";

    private static Set<String> getCommonNames(final X509Certificate certificate) {
        final var domains = new ImmutableSet.Builder<String>();
        try {
            final var x500name = new JcaX509CertificateHolder(certificate).getSubject();
            final RDN[] nameRDNs = x500name.getRDNs(BCStyle.CN);
            for (int i = 0; i < nameRDNs.length; ++i) {
                domains.add(
                        IETFUtils.valueToString(
                                x500name.getRDNs(BCStyle.CN)[i].getFirst().getValue()));
            }
            return domains.build();
        } catch (final CertificateEncodingException e) {
            return Collections.emptySet();
        }
    }

    private static Pair<String, String> parseOtherName(final byte[] otherName) {
        try {
            ASN1Primitive asn1Primitive = ASN1Primitive.fromByteArray(otherName);
            if (asn1Primitive instanceof ASN1TaggedObject taggedObject) {
                final ASN1Object inner = taggedObject.getBaseObject();
                if (inner instanceof DLSequence sequence) {
                    if (sequence.size() >= 2
                            && sequence.getObjectAt(1) instanceof ASN1TaggedObject evenInner) {
                        final String oid = sequence.getObjectAt(0).toString();
                        final ASN1Object value = evenInner.getBaseObject();
                        if (value instanceof DERUTF8String derutf8String) {
                            return new Pair<>(oid, derutf8String.getString());
                        } else if (value instanceof DERIA5String deria5String) {
                            return new Pair<>(oid, deria5String.getString());
                        }
                    }
                }
            }
            return null;
        } catch (final IOException e) {
            return null;
        }
    }

    public static boolean matchDomain(
            final String domain, final Collection<String> certificateDomains) {
        for (final String certificateDomain : certificateDomains) {
            if (certificateDomain.startsWith("*.")) {
                // https://www.rfc-editor.org/rfc/rfc6125#section-6.4.3
                // wild cards can only be in the left most label and don’t match '.'
                final var wildcardEntry = certificateDomain.substring(1);
                if (CharMatcher.is('.').countIn(wildcardEntry) < 2) {
                    Log.w(LOGTAG, "not enough labels in wildcard certificate");
                    break;
                }
                final int position = domain.indexOf('.');
                if (position != -1 && domain.substring(position).equalsIgnoreCase(wildcardEntry)) {
                    Log.d(LOGTAG, "domain " + domain + " matched " + certificateDomain);
                    return true;
                }
            } else {
                if (certificateDomain.equalsIgnoreCase(domain)) {
                    Log.d(LOGTAG, "domain " + domain + " matched " + certificateDomain);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean verify(
            final String unicodeDomain, final String unicodeHostname, SSLSession sslSession)
            throws SSLPeerUnverifiedException {
        final String domain = IDN.toASCII(unicodeDomain);
        final String hostname = unicodeHostname == null ? null : IDN.toASCII(unicodeHostname);
        final Certificate[] chain = sslSession.getPeerCertificates();
        if (chain.length == 0 || !(chain[0] instanceof X509Certificate certificate)) {
            Log.d(LOGTAG, "chain length 0");
            return false;
        }
        final var commonNames = getCommonNames(certificate);
        if (isSelfSigned(certificate)) {
            if (commonNames.size() == 1 && matchDomain(domain, commonNames)) {
                Log.d(LOGTAG, "accepted CN in self signed cert as work around for " + domain);
                return true;
            }
        }
        try {
            final ValidDomains validDomains = parseValidDomains(certificate);
            Log.d(LOGTAG, "searching for " + domain + " in " + validDomains);
            if (hostname != null) {
                Log.d(LOGTAG, "also trying to verify hostname " + hostname);
            }
            return validDomains.xmppAddresses.contains(domain)
                    || (IP.matches(domain) && validDomains.ipAddresses.contains(domain))
                    || validDomains.srvNames.contains("_xmpp-client." + domain)
                    || matchDomain(domain, validDomains.domains)
                    || (hostname != null && matchDomain(hostname, validDomains.domains));
        } catch (final Exception e) {
            return false;
        }
    }

    public static ValidDomains parseValidDomains(final X509Certificate certificate)
            throws CertificateParsingException {
        final var commonNames = getCommonNames(certificate);
        final var alternativeNames = certificate.getSubjectAlternativeNames();
        final var xmppAddresses = new ImmutableSet.Builder<String>();
        final var srvNames = new ImmutableSet.Builder<String>();
        final var domains = new ImmutableSet.Builder<String>();
        final var ips = new ImmutableSet.Builder<String>();
        if (alternativeNames.isEmpty()) {
            return new ValidDomains(
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    commonNames);
        }
        for (final var san : alternativeNames) {
            final Integer type = (Integer) san.get(0);
            if (type == 0) {
                final Pair<String, String> otherName = parseOtherName((byte[]) san.get(1));
                if (otherName != null && otherName.first != null && otherName.second != null) {
                    switch (otherName.first) {
                        case SRV_NAME:
                            srvNames.add(otherName.second.toLowerCase(Locale.US));
                            break;
                        case XMPP_ADDR:
                            xmppAddresses.add(otherName.second.toLowerCase(Locale.US));
                            break;
                        default:
                            Log.d(
                                    LOGTAG,
                                    "oid: " + otherName.first + " value: " + otherName.second);
                    }
                }
            } else if (type == 2) {
                final Object value = san.get(1);
                if (value instanceof String s) {
                    domains.add(s.toLowerCase(Locale.US));
                }
            } else if (type == 7) {
                final Object value = san.get(1);
                if (value instanceof String s) {
                    ips.add(s);
                }
            } else {
                Log.d(LOGTAG, "found more types: " + type);
            }
        }
        return new ValidDomains(
                ips.build(), xmppAddresses.build(), srvNames.build(), domains.build());
    }

    public static final class ValidDomains {
        final Set<String> ipAddresses;
        final Set<String> xmppAddresses;
        final Set<String> srvNames;
        final Set<String> domains;

        private ValidDomains(
                Set<String> ipAddresses,
                Set<String> xmppAddresses,
                Set<String> srvNames,
                Set<String> domains) {
            this.ipAddresses = ipAddresses;
            this.xmppAddresses = xmppAddresses;
            this.srvNames = srvNames;
            this.domains = domains;
        }

        public Set<String> all() {
            return new ImmutableSet.Builder<String>()
                    .addAll(xmppAddresses)
                    .addAll(srvNames)
                    .addAll(domains)
                    .build();
        }

        @Override
        @NonNull
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("ipAddresses", ipAddresses)
                    .add("xmppAddresses", xmppAddresses)
                    .add("srvNames", srvNames)
                    .add("domains", domains)
                    .toString();
        }
    }

    private boolean isSelfSigned(final X509Certificate certificate) {
        try {
            certificate.verify(certificate.getPublicKey());
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
