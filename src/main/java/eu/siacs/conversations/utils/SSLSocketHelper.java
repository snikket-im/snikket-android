package eu.siacs.conversations.utils;

import android.util.Log;

import org.conscrypt.Conscrypt;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

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

    public static void setHostname(final SSLSocket socket, final String hostname) {
        try {
            Conscrypt.setHostname(socket, hostname);
        } catch (IllegalArgumentException e) {
            Log.e(Config.LOGTAG, "unable to set SNI name on socket (" + hostname + ")", e);
        }
    }

    public static void setApplicationProtocol(final SSLSocket socket, final String protocol) {
        try {
            Conscrypt.setApplicationProtocols(socket, new String[]{protocol});
        } catch (IllegalArgumentException e) {
            Log.e(Config.LOGTAG, "unable to set ALPN on socket", e);
        }
    }

    public static SSLContext getSSLContext() throws NoSuchAlgorithmException {
        return SSLContext.getInstance("TLSv1.3");
    }

    public static void log(Account account, SSLSocket socket) {
        SSLSession session = socket.getSession();
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": protocol=" + session.getProtocol() + " cipher=" + session.getCipherSuite());
    }
}
