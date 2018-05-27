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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
import eu.siacs.conversations.xmpp.OnAdvancedStreamFeaturesLoaded;

public class HttpConnectionManager extends AbstractConnectionManager {

	public HttpConnectionManager(XmppConnectionService service) {
		super(service);
	}

	private List<HttpDownloadConnection> downloadConnections = new CopyOnWriteArrayList<>();
	private List<HttpUploadConnection> uploadConnections = new CopyOnWriteArrayList<>();

	public HttpDownloadConnection createNewDownloadConnection(Message message) {
		return this.createNewDownloadConnection(message, false);
	}

	public HttpDownloadConnection createNewDownloadConnection(Message message, boolean interactive) {
		HttpDownloadConnection connection = new HttpDownloadConnection(this);
		connection.init(message,interactive);
		this.downloadConnections.add(connection);
		return connection;
	}

	public void createNewUploadConnection(Message message, boolean delay) {
		HttpUploadConnection connection = new HttpUploadConnection(Method.determine(message.getConversation().getAccount()), this);
		connection.init(message,delay);
		this.uploadConnections.add(connection);
	}

	public boolean checkConnection(Message message) {
		final Account account = message.getConversation().getAccount();
		final URL url = message.getFileParams().url;
		if (url.getProtocol().equalsIgnoreCase(P1S3UrlStreamHandler.PROTOCOL_NAME) && account.getStatus() != Account.State.ONLINE) {
			return false;
		}
		return mXmppConnectionService.hasInternetConnection();
	}

	public void finishConnection(HttpDownloadConnection connection) {
		this.downloadConnections.remove(connection);
	}

	public void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
		this.uploadConnections.remove(httpUploadConnection);
	}

	public void setupTrustManager(final HttpsURLConnection connection, final boolean interactive) {
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

	public static Proxy getProxy() throws IOException {
		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}), 8118));
	}
}
