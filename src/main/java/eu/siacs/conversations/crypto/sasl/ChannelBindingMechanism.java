package eu.siacs.conversations.crypto.sasl;

import org.bouncycastle.jcajce.provider.digest.SHA256;
import org.conscrypt.Conscrypt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public interface ChannelBindingMechanism {

    String EXPORTER_LABEL = "EXPORTER-Channel-Binding";

    ChannelBinding getChannelBinding();

    static byte[] getChannelBindingData(final SSLSocket sslSocket, final ChannelBinding channelBinding)
            throws SaslMechanism.AuthenticationException {
        if (sslSocket == null) {
            throw new SaslMechanism.AuthenticationException("Channel binding attempt on non secure socket");
        }
        if (channelBinding == ChannelBinding.TLS_EXPORTER) {
            final byte[] keyingMaterial;
            try {
                keyingMaterial =
                        Conscrypt.exportKeyingMaterial(sslSocket, EXPORTER_LABEL, new byte[0], 32);
            } catch (final SSLException e) {
                throw new SaslMechanism.AuthenticationException("Could not export keying material");
            }
            if (keyingMaterial == null) {
                throw new SaslMechanism.AuthenticationException(
                        "Could not export keying material. Socket not ready");
            }
            return keyingMaterial;
        } else if (channelBinding == ChannelBinding.TLS_UNIQUE) {
            final byte[] unique = Conscrypt.getTlsUnique(sslSocket);
            if (unique == null) {
                throw new SaslMechanism.AuthenticationException(
                        "Could not retrieve tls unique. Socket not ready");
            }
            return unique;
        } else if (channelBinding == ChannelBinding.TLS_SERVER_END_POINT) {
            return getServerEndPointChannelBinding(sslSocket.getSession());
        } else {
            throw new SaslMechanism.AuthenticationException(
                    String.format("%s is not a valid channel binding", channelBinding));
        }
    }

    static byte[] getServerEndPointChannelBinding(final SSLSession session)
            throws SaslMechanism.AuthenticationException {
        final Certificate[] certificates;
        try {
            certificates = session.getPeerCertificates();
        } catch (final SSLPeerUnverifiedException e) {
            throw new SaslMechanism.AuthenticationException("Could not verify peer certificates");
        }
        if (certificates == null || certificates.length == 0) {
            throw new SaslMechanism.AuthenticationException("Could not retrieve peer certificate");
        }
        final X509Certificate certificate;
        if (certificates[0] instanceof X509Certificate) {
            certificate = (X509Certificate) certificates[0];
        } else {
            throw new SaslMechanism.AuthenticationException("Certificate was not X509");
        }
        final String algorithm = certificate.getSigAlgName();
        final int withIndex = algorithm.indexOf("with");
        if (withIndex <= 0) {
            throw new SaslMechanism.AuthenticationException("Unable to parse SigAlgName");
        }
        final String hashAlgorithm = algorithm.substring(0, withIndex);
        final MessageDigest messageDigest;
        // https://www.rfc-editor.org/rfc/rfc5929#section-4.1
        if ("MD5".equalsIgnoreCase(hashAlgorithm) || "SHA1".equalsIgnoreCase(hashAlgorithm)) {
            messageDigest = new SHA256.Digest();
        } else {
            try {
                messageDigest = MessageDigest.getInstance(hashAlgorithm);
            } catch (final NoSuchAlgorithmException e) {
                throw new SaslMechanism.AuthenticationException(
                        "Could not instantiate message digest for " + hashAlgorithm);
            }
        }
        final byte[] encodedCertificate;
        try {
            encodedCertificate = certificate.getEncoded();
        } catch (final CertificateEncodingException e) {
            throw new SaslMechanism.AuthenticationException("Could not encode certificate");
        }
        messageDigest.update(encodedCertificate);
        return messageDigest.digest();
    }

    static int getPriority(final SaslMechanism mechanism) {
        if (mechanism instanceof ChannelBindingMechanism) {
            final ChannelBindingMechanism channelBindingMechanism = (ChannelBindingMechanism) mechanism;
            return ChannelBinding.priority(channelBindingMechanism.getChannelBinding());
        } else {
            return 0;
        }
    }
}
