package eu.siacs.conversations.utils;

import android.content.Context;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.services.QuickConversationsService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public class TLSSocketFactory extends SSLSocketFactory {

    private final Context context;
    private final SSLSocketFactory internalSSLSocketFactory;

    public TLSSocketFactory(final X509TrustManager[] trustManager, final Context context)
            throws KeyManagementException, NoSuchAlgorithmException {
        this.context = context.getApplicationContext();
        final var sslContext = SSLSockets.getSSLContext();
        sslContext.init(null, trustManager, Random.SECURE_RANDOM);
        this.internalSSLSocketFactory = sslContext.getSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return internalSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return internalSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        return enableTLSOnSocket(
                internalSSLSocketFactory.createSocket(s, host, port, autoClose), context);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port), context);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return enableTLSOnSocket(
                internalSSLSocketFactory.createSocket(host, port, localHost, localPort), context);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(internalSSLSocketFactory.createSocket(host, port), context);
    }

    @Override
    public Socket createSocket(
            InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return enableTLSOnSocket(
                internalSSLSocketFactory.createSocket(address, port, localAddress, localPort),
                context);
    }

    private static Socket enableTLSOnSocket(final Socket socket, final Context context) {
        if (socket instanceof SSLSocket sslSocket) {
            // in Quicksy the setting for requiring TLSv1.3 is hidden; we always require it
            SSLSockets.setSecurity(
                    sslSocket,
                    QuickConversationsService.isQuicksy()
                            || new AppSettings(context).isRequireTlsV13());
        }
        return socket;
    }
}
