package eu.siacs.conversations.crypto;

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

import eu.siacs.conversations.Config;

public class CombiningTrustManager implements X509TrustManager {

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
            } catch (final CertificateException certificateException) {
                if (iterator.hasNext()) {
                    continue;
                }
                throw certificateException;
            }
        }
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        Log.d(
                Config.LOGTAG,
                CombiningTrustManager.class.getSimpleName()
                        + " is configured with "
                        + this.trustManagers.size()
                        + " TrustManagers");
        int i = 0;
        for (final Iterator<X509TrustManager> iterator = this.trustManagers.iterator();
                iterator.hasNext(); ) {
            final X509TrustManager trustManager = iterator.next();
            try {
                trustManager.checkServerTrusted(chain, authType);
                Log.d(
                        Config.LOGTAG,
                        "certificate check passed on " + trustManager.getClass().getName()+". chain length was "+chain.length);
                return;
            } catch (final CertificateException certificateException) {
                Log.d(
                        Config.LOGTAG,
                        "failed to verify in [" + i + "]/" + trustManager.getClass().getName(),
                        certificateException);
                if (iterator.hasNext()) {
                    continue;
                }
                throw certificateException;
            } finally {
                ++i;
            }
        }
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

    public static X509TrustManager combineWithDefault(final X509TrustManager... trustManagers)
            throws NoSuchAlgorithmException, KeyStoreException {
        final ImmutableList.Builder<X509TrustManager> builder = ImmutableList.builder();
        builder.addAll(Arrays.asList(trustManagers));
        builder.add(TrustManagers.createDefaultTrustManager());
        return new CombiningTrustManager(builder.build());
    }
}
