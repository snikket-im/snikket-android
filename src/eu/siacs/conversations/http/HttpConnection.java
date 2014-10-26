package eu.siacs.conversations.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.StrictHostnameVerifier;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;

import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

public class HttpConnection implements Downloadable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private Message message;
	private DownloadableFile file;
	private int mStatus = Downloadable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;

	public HttpConnection(HttpConnectionManager manager) {
		this.mHttpConnectionManager = manager;
		this.mXmppConnectionService = manager.getXmppConnectionService();
	}

	@Override
	public boolean start() {
		if (mXmppConnectionService.hasInternetConnection()) {
			if (this.mStatus == STATUS_OFFER_CHECK_FILESIZE) {
				checkFileSize(true);
			} else {
				new Thread(new FileDownloader(true)).start();
			}
			return true;
		} else {
			return false;
		}
	}

	public void init(Message message) {
		this.message = message;
		this.message.setDownloadable(this);
		try {
			mUrl = new URL(message.getBody());
			String path = mUrl.getPath();
			if (path != null && (path.endsWith(".pgp") || path.endsWith(".gpg"))) {
				this.message.setEncryption(Message.ENCRYPTION_PGP);
			}
			this.file = mXmppConnectionService.getFileBackend().getFile(
					message, false);
			String reference = mUrl.getRef();
			if (reference != null && reference.length() == 96) {
				this.file.setKey(CryptoHelper.hexToBytes(reference));
			}
			
			if (this.message.getEncryption() == Message.ENCRYPTION_OTR
					&& this.file.getKey() == null) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			checkFileSize(false);
		} catch (MalformedURLException e) {
			this.cancel();
		}
	}

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker(interactive)).start();
	}

	public void cancel() {
		mHttpConnectionManager.finishConnection(this);
		message.setDownloadable(null);
		mXmppConnectionService.updateConversationUi();
	}

	private void finish() {
		Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		intent.setData(Uri.fromFile(file));
		mXmppConnectionService.sendBroadcast(intent);
		message.setDownloadable(null);
		mHttpConnectionManager.finishConnection(this);
		mXmppConnectionService.updateConversationUi();
		if (acceptedAutomatically) {
			mXmppConnectionService.getNotificationService().push(message);
		}
	}

	private void changeStatus(int status) {
		this.mStatus = status;
		mXmppConnectionService.updateConversationUi();
	}

	private void setupTrustManager(HttpsURLConnection connection,
			boolean interactive) {
		X509TrustManager trustManager;
		HostnameVerifier hostnameVerifier;
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
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new X509TrustManager[] { trustManager },
					mXmppConnectionService.getRNG());
			connection.setSSLSocketFactory(sc.getSocketFactory());
			connection.setHostnameVerifier(hostnameVerifier);
		} catch (KeyManagementException e) {
			return;
		} catch (NoSuchAlgorithmException e) {
			return;
		}
	}

	private class FileSizeChecker implements Runnable {

		private boolean interactive = false;

		public FileSizeChecker(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			long size;
			try {
				size = retrieveFileSize();
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER_CHECK_FILESIZE);
				HttpConnection.this.acceptedAutomatically = false;
				HttpConnection.this.mXmppConnectionService.getNotificationService().push(message);
				return;
			} catch (IOException e) {
				cancel();
				return;
			}
			file.setExpectedSize(size);
			if (size <= mHttpConnectionManager.getAutoAcceptFileSize()) {
				HttpConnection.this.acceptedAutomatically = true;
				new Thread(new FileDownloader(interactive)).start();
			} else {
				changeStatus(STATUS_OFFER);
				HttpConnection.this.acceptedAutomatically = false;
				HttpConnection.this.mXmppConnectionService.getNotificationService().push(message);
			}
		}

		private long retrieveFileSize() throws IOException,
				SSLHandshakeException {
			changeStatus(STATUS_CHECKING);
			HttpURLConnection connection = (HttpURLConnection) mUrl
					.openConnection();
			connection.setRequestMethod("HEAD");
			if (connection instanceof HttpsURLConnection) {
				setupTrustManager((HttpsURLConnection) connection, interactive);
			}
			connection.connect();
			String contentLength = connection.getHeaderField("Content-Length");
			if (contentLength == null) {
				throw new IOException();
			}
			try {
				return Long.parseLong(contentLength, 10);
			} catch (NumberFormatException e) {
				throw new IOException();
			}
		}

	}

	private class FileDownloader implements Runnable {

		private boolean interactive = false;

		public FileDownloader(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			try {
				changeStatus(STATUS_DOWNLOADING);
				download();
				updateImageBounds();
				finish();
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER);
			} catch (IOException e) {
				cancel();
			}
		}

		private void download() throws SSLHandshakeException, IOException {
			HttpURLConnection connection = (HttpURLConnection) mUrl
					.openConnection();
			if (connection instanceof HttpsURLConnection) {
				setupTrustManager((HttpsURLConnection) connection, interactive);
			}
			connection.connect();
			BufferedInputStream is = new BufferedInputStream(
					connection.getInputStream());
			OutputStream os = file.createOutputStream();
			int count = -1;
			byte[] buffer = new byte[1024];
			while ((count = is.read(buffer)) != -1) {
				os.write(buffer, 0, count);
			}
			os.flush();
			os.close();
			is.close();
		}

		private void updateImageBounds() {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(file.getAbsolutePath(), options);
			int imageHeight = options.outHeight;
			int imageWidth = options.outWidth;
			message.setBody(mUrl.toString() + "|" + file.getSize() + '|'
					+ imageWidth + '|' + imageHeight);
			message.setType(Message.TYPE_IMAGE);
			mXmppConnectionService.updateMessage(message);
		}

	}

	@Override
	public int getStatus() {
		return this.mStatus;
	}

	@Override
	public long getFileSize() {
		if (this.file != null) {
			return this.file.getExpectedSize();
		} else {
			return 0;
		}
	}
}