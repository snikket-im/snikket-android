package eu.siacs.conversations.http;

import android.util.Log;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.TLSSocketFactory;

public class HttpConnectionManager extends AbstractConnectionManager {

    private final List<HttpDownloadConnection> downloadConnections = new ArrayList<>();
    private final List<HttpUploadConnection> uploadConnections = new ArrayList<>();

    public HttpConnectionManager(XmppConnectionService service) {
        super(service);
    }

    public static Proxy getProxy() throws IOException {
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), 9050));
    }

    public void createNewDownloadConnection(Message message) {
        this.createNewDownloadConnection(message, false);
    }

    public void createNewDownloadConnection(final Message message, boolean interactive) {
        synchronized (this.downloadConnections) {
            for(HttpDownloadConnection connection : this.downloadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().asBareJid() + ": download already in progress");
                    return;
                }
            }
            final HttpDownloadConnection connection = new HttpDownloadConnection(message, this);
            connection.init(interactive);
            this.downloadConnections.add(connection);
        }
    }

    public void createNewUploadConnection(final Message message, boolean delay) {
        synchronized (this.uploadConnections) {
            for (HttpUploadConnection connection : this.uploadConnections) {
                if (connection.getMessage() == message) {
                    Log.d(Config.LOGTAG, message.getConversation().getAccount().getJid().asBareJid() + ": upload already in progress");
                    return;
                }
            }
            HttpUploadConnection connection = new HttpUploadConnection(message, Method.determine(message.getConversation().getAccount()), this);
            connection.init(delay);
            this.uploadConnections.add(connection);
        }
    }

    public boolean checkConnection(Message message) {
        final Account account = message.getConversation().getAccount();
        final URL url = message.getFileParams().url;
        if (url.getProtocol().equalsIgnoreCase(P1S3UrlStreamHandler.PROTOCOL_NAME) && account.getStatus() != Account.State.ONLINE) {
            return false;
        }
        return mXmppConnectionService.hasInternetConnection();
    }

    void finishConnection(HttpDownloadConnection connection) {
        synchronized (this.downloadConnections) {
            this.downloadConnections.remove(connection);
        }
    }

    void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
        synchronized (this.uploadConnections) {
            this.uploadConnections.remove(httpUploadConnection);
        }
    }

    void setupTrustManager(final HttpsURLConnection connection, final boolean interactive) {
        final X509TrustManager trustManager;
        final HostnameVerifier hostnameVerifier = mXmppConnectionService.getMemorizingTrustManager().wrapHostnameVerifier(new StrictHostnameVerifier(), interactive);
        if (interactive) {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getInteractive();
        } else {
            trustManager = mXmppConnectionService.getMemorizingTrustManager().getNonInteractive();
        }
        try {
            final SSLSocketFactory sf = new TLSSocketFactory(new X509TrustManager[]{trustManager}, mXmppConnectionService.getRNG());
            connection.setSSLSocketFactory(sf);
            connection.setHostnameVerifier(hostnameVerifier);
        } catch (final KeyManagementException | NoSuchAlgorithmException ignored) {
        }
    }
}
