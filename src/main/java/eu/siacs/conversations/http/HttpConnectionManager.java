package eu.siacs.conversations.http;

import android.os.Build;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.SSLSocketHelper;

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

	public HttpUploadConnection createNewUploadConnection(Message message, boolean delay) {
		HttpUploadConnection connection = new HttpUploadConnection(this);
		connection.init(message,delay);
		this.uploadConnections.add(connection);
		return connection;
	}

	public void finishConnection(HttpDownloadConnection connection) {
		this.downloadConnections.remove(connection);
	}

	public void finishUploadConnection(HttpUploadConnection httpUploadConnection) {
		this.uploadConnections.remove(httpUploadConnection);
	}

	public void setupTrustManager(final HttpsURLConnection connection, final boolean interactive) {
		final X509TrustManager trustManager;
		final HostnameVerifier hostnameVerifier;
		if (interactive) {
			trustManager = mXmppConnectionService.getMemorizingTrustManager();
			hostnameVerifier = mXmppConnectionService
					.getMemorizingTrustManager().wrapHostnameVerifier(
							new StrictHostnameVerifier());
		} else {
			trustManager = mXmppConnectionService.getMemorizingTrustManager()
					.getNonInteractive();
			hostnameVerifier = mXmppConnectionService
					.getMemorizingTrustManager()
					.wrapHostnameVerifierNonInteractive(
							new StrictHostnameVerifier());
		}
		try {
			final SSLContext sc = SSLSocketHelper.getSSLContext();
			sc.init(null, new X509TrustManager[]{trustManager},
					mXmppConnectionService.getRNG());

			final SSLSocketFactory sf = sc.getSocketFactory();
			final String[] cipherSuites = CryptoHelper.getOrderedCipherSuites(
					sf.getSupportedCipherSuites());
			if (cipherSuites.length > 0) {
				sc.getDefaultSSLParameters().setCipherSuites(cipherSuites);

			}

			connection.setSSLSocketFactory(sf);
			connection.setHostnameVerifier(hostnameVerifier);
		} catch (final KeyManagementException | NoSuchAlgorithmException ignored) {
		}
	}

	public Proxy getProxy() throws IOException {
		return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(InetAddress.getLocalHost(), 8118));
	}
}
