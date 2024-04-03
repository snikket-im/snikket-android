package eu.siacs.conversations.crypto;

import android.content.Context;

import androidx.annotation.Nullable;

import com.google.common.collect.Iterables;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.R;

public final class TrustManagers {

    private TrustManagers() {
        throw new IllegalStateException("Do not instantiate me");
    }

    public static X509TrustManager createTrustManager(@Nullable final KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        final TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        return Iterables.getOnlyElement(
                Iterables.filter(
                        Arrays.asList(trustManagerFactory.getTrustManagers()),
                        X509TrustManager.class));
    }

    public static X509TrustManager createDefaultTrustManager()
            throws NoSuchAlgorithmException, KeyStoreException {
        return createTrustManager(null);
    }

    public static X509TrustManager defaultWithBundledLetsEncrypt(final Context context)
            throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        final BundledTrustManager bundleTrustManager =
                BundledTrustManager.builder()
                        .loadKeyStore(
                                context.getResources().openRawResource(R.raw.letsencrypt),
                                "letsencrypt")
                        .build();
        return CombiningTrustManager.combineWithDefault(bundleTrustManager);
    }


}
