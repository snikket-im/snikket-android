package eu.siacs.conversations.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

public class BundledTrustManager implements X509TrustManager {

    private final X509TrustManager delegate;

    private BundledTrustManager(final KeyStore keyStore)
            throws NoSuchAlgorithmException, KeyStoreException {
        this.delegate = TrustManagers.createTrustManager(keyStore);
    }

    public static Builder builder() throws KeyStoreException {
        return new Builder();
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        this.delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        this.delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return this.delegate.getAcceptedIssuers();
    }

    public static class Builder {

        private KeyStore keyStore;

        private Builder() {}

        public Builder loadKeyStore(final InputStream inputStream, final String password)
                throws CertificateException, IOException, NoSuchAlgorithmException,
                        KeyStoreException {
            if (this.keyStore != null) {
                throw new IllegalStateException("KeyStore has already been loaded");
            }
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(inputStream, password.toCharArray());
            this.keyStore = keyStore;
            return this;
        }

        public BundledTrustManager build() throws NoSuchAlgorithmException, KeyStoreException {
            return new BundledTrustManager(keyStore);
        }
    }
}
