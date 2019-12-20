package eu.siacs.conversations.http;

import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.common.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CancellationException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.FileWriterException;
import eu.siacs.conversations.utils.WakeLockHelper;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import rocks.xmpp.addr.Jid;

public class HttpDownloadConnection implements Transferable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private final Message message;
	private DownloadableFile file;
	private int mStatus = Transferable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;
	private int mProgress = 0;
	private final boolean mUseTor;
	private boolean canceled = false;
	private Method method = Method.HTTP_UPLOAD;

	HttpDownloadConnection(Message message, HttpConnectionManager manager) {
		this.message = message;
		this.mHttpConnectionManager = manager;
		this.mXmppConnectionService = manager.getXmppConnectionService();
		this.mUseTor = mXmppConnectionService.useTorToConnect();
	}

	@Override
	public boolean start() {
		if (mXmppConnectionService.hasInternetConnection()) {
			if (this.mStatus == STATUS_OFFER_CHECK_FILESIZE) {
				checkFileSize(true);
			} else {
				download(true);
			}
			return true;
		} else {
			return false;
		}
	}

	public void init(boolean interactive) {
		this.message.setTransferable(this);
		try {
			if (message.hasFileOnRemoteHost()) {
				mUrl = CryptoHelper.toHttpsUrl(message.getFileParams().url);
			} else {
				mUrl = CryptoHelper.toHttpsUrl(new URL(message.getBody().split("\n")[0]));
			}
			final AbstractConnectionManager.Extension extension = AbstractConnectionManager.Extension.of(mUrl.getPath());
			if (VALID_CRYPTO_EXTENSIONS.contains(extension.main)) {
				this.message.setEncryption(Message.ENCRYPTION_PGP);
			} else if (message.getEncryption() != Message.ENCRYPTION_OTR
					&& message.getEncryption() != Message.ENCRYPTION_AXOLOTL) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			final String ext;
			if (VALID_CRYPTO_EXTENSIONS.contains(extension.main)) {
				ext = extension.secondary;
			} else {
				ext = extension.main;
			}
			message.setRelativeFilePath(message.getUuid() + (ext != null ? ("." + ext) : ""));
			if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
				this.file = new DownloadableFile(mXmppConnectionService.getCacheDir().getAbsolutePath() + "/" + message.getUuid());
				Log.d(Config.LOGTAG, "create temporary OMEMO encrypted file: " + this.file.getAbsolutePath() + "(" + message.getMimeType() + ")");
			} else {
				this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
			}
			final String reference = mUrl.getRef();
			if (reference != null && AesGcmURLStreamHandler.IV_KEY.matcher(reference).matches()) {
				this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
			}

			if (this.message.getEncryption() == Message.ENCRYPTION_AXOLOTL && this.file.getKey() == null) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			method = mUrl.getProtocol().equalsIgnoreCase(P1S3UrlStreamHandler.PROTOCOL_NAME) ? Method.P1_S3 : Method.HTTP_UPLOAD;
			long knownFileSize = message.getFileParams().size;
			if (knownFileSize > 0 && interactive && method != Method.P1_S3) {
				this.file.setExpectedSize(knownFileSize);
				download(true);
			} else {
				checkFileSize(interactive);
			}
		} catch (MalformedURLException e) {
			this.cancel();
		}
	}

	private void download(boolean interactive) {
		new Thread(new FileDownloader(interactive)).start();
	}

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker(interactive)).start();
	}

	@Override
	public void cancel() {
		this.canceled = true;
		mHttpConnectionManager.finishConnection(this);
		message.setTransferable(null);
		if (message.isFileOrImage()) {
			message.setDeleted(true);
		}
		mHttpConnectionManager.updateConversationUi(true);
	}

	private void decryptOmemoFile() throws Exception {
		final DownloadableFile outputFile = mXmppConnectionService.getFileBackend().getFile(message, true);

		if (outputFile.getParentFile().mkdirs()) {
			Log.d(Config.LOGTAG, "created parent directories for " + outputFile.getAbsolutePath());
		}

		try {
			outputFile.createNewFile();
			final InputStream is = new FileInputStream(this.file);

			outputFile.setKey(this.file.getKey());
			outputFile.setIv(this.file.getIv());
			final OutputStream os = AbstractConnectionManager.createOutputStream(outputFile, false, true);

			ByteStreams.copy(is, os);

			FileBackend.close(is);
			FileBackend.close(os);

			if (!file.delete()) {
				Log.w(Config.LOGTAG,"unable to delete temporary OMEMO encrypted file " + file.getAbsolutePath());
			}

			message.setRelativeFilePath(outputFile.getPath());
		} catch (IOException e) {
			message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
			mXmppConnectionService.updateMessage(message);
		}
	}

	private void finish() throws Exception {
		if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL)	{
			decryptOmemoFile();
		}
		message.setTransferable(null);
		mHttpConnectionManager.finishConnection(this);
		boolean notify = acceptedAutomatically && !message.isRead();
		if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			notify = message.getConversation().getAccount().getPgpDecryptionService().decrypt(message, notify);
		}
		mHttpConnectionManager.updateConversationUi(true);
		final boolean notifyAfterScan = notify;
		mXmppConnectionService.getFileBackend().updateMediaScanner(file, () -> {
			if (notifyAfterScan) {
				mXmppConnectionService.getNotificationService().push(message);
			}
		});
	}

	private void changeStatus(int status) {
		this.mStatus = status;
		mHttpConnectionManager.updateConversationUi(true);
	}

	private void showToastForException(Exception e) {
		if (e instanceof java.net.UnknownHostException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_server_not_found);
		} else if (e instanceof java.net.ConnectException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_connect);
		} else if (e instanceof FileWriterException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_write_file);
		} else if (!(e instanceof CancellationException)) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_file_not_found);
		}
	}

	private void updateProgress(long i) {
		this.mProgress = (int) i;
		mHttpConnectionManager.updateConversationUi(false);
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

	@Override
	public int getProgress() {
		return this.mProgress;
	}

	public Message getMessage() {
		return message;
	}

	private class FileSizeChecker implements Runnable {

		private final boolean interactive;

		FileSizeChecker(boolean interactive) {
			this.interactive = interactive;
		}


		@Override
		public void run() {
			if (mUrl.getProtocol().equalsIgnoreCase(P1S3UrlStreamHandler.PROTOCOL_NAME)) {
				retrieveUrl();
			} else {
				check();
			}
		}

		private void retrieveUrl() {
			changeStatus(STATUS_CHECKING);
			final Account account = message.getConversation().getAccount();
			IqPacket request = mXmppConnectionService.getIqGenerator().requestP1S3Url(Jid.of(account.getJid().getDomain()), mUrl.getHost());
			mXmppConnectionService.sendIqPacket(message.getConversation().getAccount(), request, (a, packet) -> {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					String download = packet.query().getAttribute("download");
					if (download != null) {
						try {
							mUrl = new URL(download);
							check();
							return;
						} catch (MalformedURLException e) {
							//fallthrough
						}
					}
				}
				Log.d(Config.LOGTAG,"unable to retrieve actual download url");
				retrieveFailed(null);
			});
		}

		private void retrieveFailed(@Nullable Exception e) {
			changeStatus(STATUS_OFFER_CHECK_FILESIZE);
			if (interactive) {
				if (e != null) {
					showToastForException(e);
				}
			} else {
				HttpDownloadConnection.this.acceptedAutomatically = false;
				HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
			}
			cancel();
		}

		private void check() {
			long size;
			try {
				size = retrieveFileSize();
			} catch (Exception e) {
				Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
				retrieveFailed(e);
				return;
			}
			file.setExpectedSize(size);
			message.resetFileParams();
			if (mHttpConnectionManager.hasStoragePermission()
					&& size <= mHttpConnectionManager.getAutoAcceptFileSize()
					&& mXmppConnectionService.isDataSaverDisabled()) {
				HttpDownloadConnection.this.acceptedAutomatically = true;
				download(interactive);
			} else {
				changeStatus(STATUS_OFFER);
				HttpDownloadConnection.this.acceptedAutomatically = false;
				HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
			}
		}

		private long retrieveFileSize() throws IOException {
			try {
				Log.d(Config.LOGTAG, "retrieve file size. interactive:" + String.valueOf(interactive));
				changeStatus(STATUS_CHECKING);
				HttpURLConnection connection;
				final String hostname = mUrl.getHost();
				final boolean onion = hostname != null && hostname.endsWith(".onion");
				if (mUseTor || message.getConversation().getAccount().isOnion() || onion) {
					connection = (HttpURLConnection) mUrl.openConnection(HttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mUrl.openConnection();
				}
				if (method == Method.P1_S3) {
					connection.setRequestMethod("GET");
					connection.addRequestProperty("Range","bytes=0-0");
				} else {
					connection.setRequestMethod("HEAD");
				}
				connection.setUseCaches(false);
				Log.d(Config.LOGTAG, "url: " + connection.getURL().toString());
				connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getUserAgent());
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
				}
				connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.connect();
				String contentLength;
				if (method == Method.P1_S3) {
					String contentRange = connection.getHeaderField("Content-Range");
					String[] contentRangeParts = contentRange == null ? new String[0] : contentRange.split("/");
					if (contentRangeParts.length != 2) {
						contentLength = null;
					} else {
						contentLength = contentRangeParts[1];
					}
				} else {
					contentLength = connection.getHeaderField("Content-Length");
				}
				connection.disconnect();
				if (contentLength == null) {
					throw new IOException("no content-length found in HEAD response");
				}
				return Long.parseLong(contentLength, 10);
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "io exception during HEAD " + e.getMessage());
				throw e;
			} catch (NumberFormatException e) {
				throw new IOException();
			}
		}

	}

	private class FileDownloader implements Runnable {

		private final boolean interactive;

		private OutputStream os;

		public FileDownloader(boolean interactive) {
			this.interactive = interactive;
		}

		@Override
		public void run() {
			try {
				changeStatus(STATUS_DOWNLOADING);
				download();
				finish();
				updateImageBounds();
			} catch (SSLHandshakeException e) {
				changeStatus(STATUS_OFFER);
			} catch (Exception e) {
				if (interactive) {
					showToastForException(e);
				} else {
					HttpDownloadConnection.this.acceptedAutomatically = false;
					HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
				}
				cancel();
			}
		}

		private void download() throws Exception {
			InputStream is = null;
			HttpURLConnection connection = null;
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_" + message.getUuid());
			try {
				wakeLock.acquire();
				if (mUseTor || message.getConversation().getAccount().isOnion()) {
					connection = (HttpURLConnection) mUrl.openConnection(HttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mUrl.openConnection();
				}
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
				}
				connection.setUseCaches(false);
				connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getUserAgent());
				final long expected = file.getExpectedSize();
				final boolean tryResume = file.exists() && file.getSize() > 0 && file.getSize() < expected;
				long resumeSize = 0;

				if (tryResume) {
					resumeSize = file.getSize();
					Log.d(Config.LOGTAG, "http download trying resume after " + resumeSize + " of " + expected);
					connection.setRequestProperty("Range", "bytes=" + resumeSize + "-");
				}
				connection.setConnectTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.setReadTimeout(Config.SOCKET_TIMEOUT * 1000);
				connection.connect();
				is = new BufferedInputStream(connection.getInputStream());
				final String contentRange = connection.getHeaderField("Content-Range");
				boolean serverResumed = tryResume && contentRange != null && contentRange.startsWith("bytes " + resumeSize + "-");
				long transmitted = 0;
				if (tryResume && serverResumed) {
					Log.d(Config.LOGTAG, "server resumed");
					transmitted = file.getSize();
					updateProgress(Math.round(((double) transmitted / expected) * 100));
					os = AbstractConnectionManager.createOutputStream(file, true, false);
					if (os == null) {
						throw new FileWriterException();
					}
				} else {
					long reportedContentLengthOnGet;
					try {
						reportedContentLengthOnGet = Long.parseLong(connection.getHeaderField("Content-Length"));
					} catch (NumberFormatException | NullPointerException e) {
						reportedContentLengthOnGet = 0;
					}
					if (expected != reportedContentLengthOnGet) {
						Log.d(Config.LOGTAG, "content-length reported on GET (" + reportedContentLengthOnGet + ") did not match Content-Length reported on HEAD (" + expected + ")");
					}
					file.getParentFile().mkdirs();
					if (!file.exists() && !file.createNewFile()) {
						throw new FileWriterException();
					}
					os = AbstractConnectionManager.createOutputStream(file, false, false);
				}
				int count;
				byte[] buffer = new byte[4096];
				while ((count = is.read(buffer)) != -1) {
					transmitted += count;
					try {
						os.write(buffer, 0, count);
					} catch (IOException e) {
						throw new FileWriterException();
					}
					updateProgress(Math.round(((double) transmitted / expected) * 100));
					if (canceled) {
						throw new CancellationException();
					}
				}
				try {
					os.flush();
				} catch (IOException e) {
					throw new FileWriterException();
				}
			} catch (CancellationException | IOException e) {
				Log.d(Config.LOGTAG, "http download failed " + e.getMessage());
				throw e;
			} finally {
				FileBackend.close(os);
				FileBackend.close(is);
				if (connection != null) {
					connection.disconnect();
				}
				WakeLockHelper.release(wakeLock);
			}
		}

		private void updateImageBounds() {
			final boolean privateMessage = message.isPrivateMessage();
			message.setType(privateMessage ? Message.TYPE_PRIVATE_FILE : Message.TYPE_FILE);
			final URL url;
			final String ref = mUrl.getRef();
			if (method == Method.P1_S3) {
				url = message.getFileParams().url;
			} else if (ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches()) {
				url = CryptoHelper.toAesGcmUrl(mUrl);
			} else {
				url = mUrl;
			}
			mXmppConnectionService.getFileBackend().updateFileParams(message, url);
			mXmppConnectionService.updateMessage(message);
		}

	}
}
