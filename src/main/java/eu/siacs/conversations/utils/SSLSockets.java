package eu.siacs.conversations.utils;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.common.base.Strings;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;

import org.conscrypt.Conscrypt;

import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

public class SSLSockets {

    public static void setSecurity(final SSLSocket sslSocket) {
        final String[] supportProtocols;
        final Collection<String> supportedProtocols =
                new LinkedList<>(Arrays.asList(sslSocket.getSupportedProtocols()));
        supportedProtocols.remove("SSLv3");
        supportProtocols = supportedProtocols.toArray(new String[0]);

        sslSocket.setEnabledProtocols(supportProtocols);

        final String[] cipherSuites =
                CryptoHelper.getOrderedCipherSuites(sslSocket.getSupportedCipherSuites());
        if (cipherSuites.length > 0) {
            sslSocket.setEnabledCipherSuites(cipherSuites);
        }
    }

    public static void setHostname(final SSLSocket socket, final String hostname) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setHostname(socket, hostname);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            setHostnameNougat(socket, hostname);
        } else {
            setHostnameReflection(socket, hostname);
        }
    }

    private static void setHostnameReflection(final SSLSocket socket, final String hostname) {
        try {
            socket.getClass().getMethod("setHostname", String.class).invoke(socket, hostname);
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "unable to set SNI name on socket (" + hostname + ")", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static void setHostnameNougat(final SSLSocket socket, final String hostname) {
        final SSLParameters parameters = new SSLParameters();
        parameters.setServerNames(Collections.singletonList(new SNIHostName(hostname)));
        socket.setSSLParameters(parameters);
    }

    private static void setApplicationProtocolReflection(
            final SSLSocket socket, final String protocol) {
        try {
            final Method method = socket.getClass().getMethod("setAlpnProtocols", byte[].class);
            // the concatenation of 8-bit, length prefixed protocol names, just one in our case...
            // http://tools.ietf.org/html/draft-agl-tls-nextprotoneg-04#page-4
            final byte[] protocolUTF8Bytes = protocol.getBytes(StandardCharsets.UTF_8);
            final byte[] lengthPrefixedProtocols = new byte[protocolUTF8Bytes.length + 1];
            lengthPrefixedProtocols[0] = (byte) protocol.length(); // cannot be over 255 anyhow
            System.arraycopy(
                    protocolUTF8Bytes, 0, lengthPrefixedProtocols, 1, protocolUTF8Bytes.length);
            method.invoke(socket, new Object[] {lengthPrefixedProtocols});
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, "unable to set ALPN on socket", e);
        }
    }

    public static void setApplicationProtocol(final SSLSocket socket, final String protocol) {
        if (Conscrypt.isConscrypt(socket)) {
            Conscrypt.setApplicationProtocols(socket, new String[] {protocol});
        } else {
            setApplicationProtocolReflection(socket, protocol);
        }
    }

    public static SSLContext getSSLContext() throws NoSuchAlgorithmException {
        try {
            return SSLContext.getInstance("TLSv1.3");
        } catch (NoSuchAlgorithmException e) {
            return SSLContext.getInstance("TLSv1.2");
        }
    }

    public static void log(Account account, SSLSocket socket) {
        SSLSession session = socket.getSession();
        Log.d(
                Config.LOGTAG,
                account.getJid().asBareJid()
                        + ": protocol="
                        + session.getProtocol()
                        + " cipher="
                        + session.getCipherSuite());
    }

    public static Version version(final Socket socket) {
        if (socket instanceof SSLSocket sslSocket) {
            if (Conscrypt.isConscrypt(sslSocket)) {
                return Version.of(sslSocket.getSession().getProtocol());
            } else {
                return Version.TLS_UNSUPPORTED_VERSION;
            }
        } else {
            return Version.NONE;
        }
    }

    public enum Version {
        TLS_1_0,
        TLS_1_1,
        TLS_1_2,
        TLS_1_3,
        TLS_UNSUPPORTED_VERSION,
        NONE;

        private static Version of(final String protocol) {
            return switch (Strings.nullToEmpty(protocol)) {
                case "TLSv1" -> TLS_1_0;
                case "TLSv1.1" -> TLS_1_1;
                case "TLSv1.2" -> TLS_1_2;
                case "TLSv1.3" -> TLS_1_3;
                default -> TLS_UNSUPPORTED_VERSION;
            };
        }
    }
}
