package de.gultsch.common;

import android.annotation.SuppressLint;
import android.util.Log;
import com.google.common.collect.ImmutableList;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import javax.net.ssl.X509TrustManager;

@SuppressLint("CustomX509TrustManager")
public final class CombiningTrustManager implements X509TrustManager {

    private final List<X509TrustManager> trustManagers;

    private CombiningTrustManager(final List<X509TrustManager> trustManagers) {
        this.trustManagers = trustManagers;
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        for (final Iterator<X509TrustManager> iterator = this.trustManagers.iterator();
                iterator.hasNext(); ) {
            final X509TrustManager trustManager = iterator.next();
            try {
                trustManager.checkClientTrusted(chain, authType);
                return;
            } catch (final CertificateException certificateException) {
                if (iterator.hasNext()) {
                    continue;
                }
                throw certificateException;
            }
        }
        throw new CertificateException("No trust managers configured");
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        Log.d(
                CombiningTrustManager.class.getSimpleName(),
                "configured with " + this.trustManagers.size() + " TrustManagers");
        for (final Iterator<X509TrustManager> iterator = this.trustManagers.iterator();
                iterator.hasNext(); ) {
            final X509TrustManager trustManager = iterator.next();
            try {
                trustManager.checkServerTrusted(chain, authType);
                return;
            } catch (final CertificateException certificateException) {
                if (iterator.hasNext()) {
                    continue;
                }
                throw certificateException;
            }
        }
        throw new CertificateException("No trust managers configured");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        final ImmutableList.Builder<X509Certificate> certificates = ImmutableList.builder();
        for (final X509TrustManager trustManager : this.trustManagers) {
            for (final X509Certificate certificate : trustManager.getAcceptedIssuers()) {
                certificates.add(certificate);
            }
        }
        return certificates.build().toArray(new X509Certificate[0]);
    }

    static X509TrustManager combineWithDefault(final X509TrustManager... trustManagers)
            throws NoSuchAlgorithmException, KeyStoreException {
        final ImmutableList.Builder<X509TrustManager> builder = ImmutableList.builder();
        builder.addAll(Arrays.asList(trustManagers));
        builder.add(TrustManagers.createDefaultTrustManager());
        return new CombiningTrustManager(builder.build());
    }
}
