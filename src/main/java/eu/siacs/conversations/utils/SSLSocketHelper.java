package eu.siacs.conversations.utils;

import android.util.Log;

import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;

public class SSLSocketHelper {

    public static void setSecurity(final SSLSocket sslSocket) {
        final String[] supportProtocols;
        final Collection<String> supportedProtocols = new LinkedList<>(
                Arrays.asList(sslSocket.getSupportedProtocols()));
        supportedProtocols.remove("SSLv3");
        supportProtocols = supportedProtocols.toArray(new String[supportedProtocols.size()]);

        sslSocket.setEnabledProtocols(supportProtocols);

        final String[] cipherSuites = CryptoHelper.getOrderedCipherSuites(
                sslSocket.getSupportedCipherSuites());
        if (cipherSuites.length > 0) {
            sslSocket.setEnabledCipherSuites(cipherSuites);
        }
    }

    public static void setSNIHost(final SSLSocketFactory factory, final SSLSocket socket, final String hostname) {
        if (factory instanceof android.net.SSLCertificateSocketFactory) {
            ((android.net.SSLCertificateSocketFactory) factory).setHostname(socket, hostname);
        }
    }

    public static void setAlpnProtocol(final SSLSocketFactory factory, final SSLSocket socket, final String protocol) {
        try {
            if (factory instanceof android.net.SSLCertificateSocketFactory && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                // can't call directly because of @hide?
                //((android.net.SSLCertificateSocketFactory)factory).setAlpnProtocols(new byte[][]{protocol.getBytes("UTF-8")});
                android.net.SSLCertificateSocketFactory.class.getMethod("setAlpnProtocols", byte[][].class).invoke(socket, new Object[]{new byte[][]{protocol.getBytes("UTF-8")}});
            } else {
                final Method method = socket.getClass().getMethod("setAlpnProtocols", byte[].class);
                // the concatenation of 8-bit, length prefixed protocol names, just one in our case...
                // http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
                final byte[] protocolUTF8Bytes = protocol.getBytes("UTF-8");
                final byte[] lengthPrefixedProtocols = new byte[protocolUTF8Bytes.length + 1];
                lengthPrefixedProtocols[0] = (byte) protocol.length(); // cannot be over 255 anyhow
                System.arraycopy(protocolUTF8Bytes, 0, lengthPrefixedProtocols, 1, protocolUTF8Bytes.length);
                method.invoke(socket, new Object[]{lengthPrefixedProtocols});
            }
        } catch (Throwable e) {
            // ignore any error, we just can't set the alpn protocol...
        }
    }

    public static SSLContext getSSLContext() throws NoSuchAlgorithmException {
        return SSLContext.getInstance("TLSv1.3");
    }

    public static void log(Account account, SSLSocket socket) {
        SSLSession session = socket.getSession();
        Log.d(Config.LOGTAG,account.getJid().asBareJid()+": protocol="+session.getProtocol()+" cipher="+session.getCipherSuite());
    }
}
