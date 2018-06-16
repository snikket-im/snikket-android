package eu.siacs.conversations.http;

import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
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
import eu.siacs.conversations.entities.TransferablePlaceholder;
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
	private Message message;
	private DownloadableFile file;
	private int mStatus = Transferable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;
	private int mProgress = 0;
	private final boolean mUseTor;
	private boolean canceled = false;
	private Method method = Method.HTTP_UPLOAD;

	public HttpDownloadConnection(HttpConnectionManager manager) {
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
				new Thread(new FileDownloader(true)).start();
			}
			return true;
		} else {
			return false;
		}
	}

	public void init(Message message) {
		init(message, false);
	}

	public void init(Message message, boolean interactive) {
		this.message = message;
		this.message.setTransferable(this);
		try {
			if (message.hasFileOnRemoteHost()) {
				mUrl = CryptoHelper.toHttpsUrl(message.getFileParams().url);
			} else {
				mUrl = CryptoHelper.toHttpsUrl(new URL(message.getBody().split("\n")[0]));
			}
			String[] parts = mUrl.getPath().toLowerCase().split("\\.");
			String lastPart = parts.length >= 1 ? parts[parts.length - 1] : null;
			String secondToLast = parts.length >= 2 ? parts[parts.length - 2] : null;
			if ("pgp".equals(lastPart) || "gpg".equals(lastPart)) {
				this.message.setEncryption(Message.ENCRYPTION_PGP);
			} else if (message.getEncryption() != Message.ENCRYPTION_OTR
					&& message.getEncryption() != Message.ENCRYPTION_AXOLOTL) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			String extension;
			if (VALID_CRYPTO_EXTENSIONS.contains(lastPart)) {
				extension = secondToLast;
			} else {
				extension = lastPart;
			}
			message.setRelativeFilePath(message.getUuid() + (extension != null ? ("." + extension) : ""));
			this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
			final String reference = mUrl.getRef();
			if (reference != null && AesGcmURLStreamHandler.IV_KEY.matcher(reference).matches()) {
				this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
			}

			if (this.message.getEncryption() == Message.ENCRYPTION_AXOLOTL && this.file.getKey() == null) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
			}
			method = mUrl.getProtocol().equalsIgnoreCase(P1S3UrlStreamHandler.PROTOCOL_NAME) ? Method.P1_S3 : Method.HTTP_UPLOAD;
			checkFileSize(interactive);
		} catch (MalformedURLException e) {
			this.cancel();
		}
	}

	private void checkFileSize(boolean interactive) {
		new Thread(new FileSizeChecker(interactive)).start();
	}

	@Override
	public void cancel() {
		this.canceled = true;
		mHttpConnectionManager.finishConnection(this);
		if (message.isFileOrImage()) {
			message.setTransferable(new TransferablePlaceholder(Transferable.STATUS_DELETED));
		} else {
			message.setTransferable(null);
		}
		mHttpConnectionManager.updateConversationUi(true);
	}

	private void finish() {
		mXmppConnectionService.getFileBackend().updateMediaScanner(file);
		message.setTransferable(null);
		mHttpConnectionManager.finishConnection(this);
		boolean notify = acceptedAutomatically && !message.isRead();
		if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			notify = message.getConversation().getAccount().getPgpDecryptionService().decrypt(message, notify);
		}
		mHttpConnectionManager.updateConversationUi(true);
		if (notify) {
			mXmppConnectionService.getNotificationService().push(message);
		}
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
				new Thread(new FileDownloader(interactive)).start();
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
				if (mUseTor || message.getConversation().getAccount().isOnion()) {
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
				connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
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
				updateImageBounds();
				finish();
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
				connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
				final boolean tryResume = file.exists() && file.getKey() == null && file.getSize() > 0;
				long resumeSize = 0;
				long expected = file.getExpectedSize();
				if (tryResume) {
					resumeSize = file.getSize();
					Log.d(Config.LOGTAG, "http download trying resume after" + resumeSize + " of " + expected);
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
					os = AbstractConnectionManager.createAppendedOutputStream(file);
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
					os = AbstractConnectionManager.createOutputStream(file, true);
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
			message.setType(Message.TYPE_FILE);
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
