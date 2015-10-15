package eu.siacs.conversations.crypto;

import android.util.Log;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class XmppDomainVerifier implements HostnameVerifier {

	private final String LOGTAG = "XmppDomainVerifier";

	@Override
	public boolean verify(String domain, SSLSession sslSession) {
		try {
			X509Certificate[] chain = (X509Certificate[]) sslSession.getPeerCertificates();
			Collection<List<?>> alternativeNames = chain[0].getSubjectAlternativeNames();
			List<String> xmppAddrs = new ArrayList<>();
			List<String> srvNames = new ArrayList<>();
			List<String> domains = new ArrayList<>();
			if (alternativeNames != null) {
				for(List<?> san : alternativeNames) {
					Integer type = (Integer) san.get(0);
					if (type == 0) {
						try {
							ASN1Primitive asn1Primitive = ASN1Primitive.fromByteArray((byte[]) san.get(1));
							if (asn1Primitive instanceof  DERTaggedObject) {
								ASN1Primitive inner = ((DERTaggedObject) asn1Primitive).getObject();
								if (inner instanceof  DLSequence) {
									DLSequence sequence = (DLSequence) inner;
									if (sequence.size() >= 2 && sequence.getObjectAt(1) instanceof DERTaggedObject) {
										String oid = sequence.getObjectAt(0).toString();
										ASN1Primitive value  = ((DERTaggedObject) sequence.getObjectAt(1)).getObject();
										switch (oid) {
											case "1.3.6.1.5.5.7.8.5":
												if (value instanceof DERUTF8String) {
													xmppAddrs.add(((DERUTF8String) value).getString());
												} else if (value instanceof DERIA5String) {
													xmppAddrs.add(((DERIA5String) value).getString());
												}
												break;
											case "1.3.6.1.5.5.7.8.7":
												if (value instanceof DERUTF8String) {
													srvNames.add(((DERUTF8String) value).getString());
												} else if (value instanceof DERIA5String) {
													srvNames.add(((DERIA5String) value).getString());
												}
												break;
											default:
												Log.d(LOGTAG,"value was of type:"+value.getClass().getName()+ " oid was:"+oid);
										}
									}
								}
							}
						} catch (IOException e) {
							//ignored
						}
					} else if (type == 2) {
						Object value = san.get(1);
						if (value instanceof String) {
							domains.add((String) value);
						}
					}
				}
			}
			if (srvNames.size() == 0 && xmppAddrs.size() == 0 && domains.size() == 0) {
				X500Name x500name = new JcaX509CertificateHolder(chain[0]).getSubject();
				RDN[] rdns = x500name.getRDNs(BCStyle.CN);
				for(int i = 0; i < rdns.length; ++i) {
					domains.add(IETFUtils.valueToString(x500name.getRDNs(BCStyle.CN)[i].getFirst().getValue()));
				}
			}
			Log.d(LOGTAG, "searching for " + domain + " in srvNames: " + srvNames + " xmppAddrs: " + xmppAddrs + " domains:" + domains);
			return xmppAddrs.contains(domain) || srvNames.contains("_xmpp-client."+domain) || matchDomain(domain, domains);
		} catch (Exception e) {
			return false;
		}
	}

	private boolean matchDomain(String needle, List<String> haystack) {
		for(String entry : haystack) {
			if (entry.startsWith("*.")) {
				int i = needle.indexOf('.');
				if (i != -1 && needle.substring(i).equals(entry.substring(2))) {
					Log.d(LOGTAG,"domain "+needle+" matched "+entry);
					return true;
				}
			} else {
				if (entry.equals(needle)) {
					Log.d(LOGTAG,"domain "+needle+" matched "+entry);
					return true;
				}
			}
		}
		return false;
	}
}
