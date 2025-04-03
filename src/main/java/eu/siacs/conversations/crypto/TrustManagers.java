package eu.siacs.conversations.crypto;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.common.collect.Iterables;
import eu.siacs.conversations.R;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class TrustManagers {

    private static final char[] BUNDLED_KEYSTORE_PASSWORD = "letsencrypt".toCharArray();

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

    public static X509TrustManager createDefaultWithBundledLetsEncrypt(final Context context)
            throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
        final var bundleTrustManager =
                createWithKeyStore(context.getResources().openRawResource(R.raw.letsencrypt));
        return CombiningTrustManager.combineWithDefault(bundleTrustManager);
    }

    private static X509TrustManager createWithKeyStore(final InputStream inputStream)
            throws CertificateException, IOException, NoSuchAlgorithmException, KeyStoreException {
        final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(inputStream, BUNDLED_KEYSTORE_PASSWORD);
        return TrustManagers.createTrustManager(keyStore);
    }
}
