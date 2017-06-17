package de.duenndns.ssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public interface DomainHostnameVerifier extends HostnameVerifier {

    boolean verify(String domain, String hostname, SSLSession sslSession);

}
