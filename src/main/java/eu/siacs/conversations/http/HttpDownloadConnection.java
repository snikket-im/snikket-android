package eu.siacs.conversations.http;

import android.os.PowerManager;
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
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.entities.TransferablePlaceholder;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

public class HttpDownloadConnection implements Transferable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private Message message;
	private DownloadableFile file;
	private int mStatus = Transferable.STATUS_UNKNOWN;
	private boolean acceptedAutomatically = false;
	private int mProgress = 0;
	private boolean mUseTor = false;
	private boolean canceled = false;

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
				mUrl = message.getFileParams().url;
			} else {
				mUrl = new URL(message.getBody());
			}
			String[] parts = mUrl.getPath().toLowerCase().split("\\.");
			String lastPart = parts.length >= 1 ? parts[parts.length - 1] : null;
			String secondToLast = parts.length >= 2 ? parts[parts.length -2] : null;
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
			message.setRelativeFilePath(message.getUuid() + "." + extension);
			this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
			String reference = mUrl.getRef();
			if (reference != null && reference.length() == 96) {
				this.file.setKeyAndIv(CryptoHelper.hexToBytes(reference));
			}

			if ((this.message.getEncryption() == Message.ENCRYPTION_OTR
					|| this.message.getEncryption() == Message.ENCRYPTION_AXOLOTL)
					&& this.file.getKey() == null) {
				this.message.setEncryption(Message.ENCRYPTION_NONE);
					}
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
		mXmppConnectionService.updateConversationUi();
	}

	private void finish() {
		mXmppConnectionService.getFileBackend().updateMediaScanner(file);
		message.setTransferable(null);
		mHttpConnectionManager.finishConnection(this);
		if (message.getEncryption() == Message.ENCRYPTION_PGP) {
			message.getConversation().getAccount().getPgpDecryptionService().add(message);
		}
		mXmppConnectionService.updateConversationUi();
		if (acceptedAutomatically) {
			mXmppConnectionService.getNotificationService().push(message);
		}
	}

	private void changeStatus(int status) {
		this.mStatus = status;
		mXmppConnectionService.updateConversationUi();
	}

	private class WriteException extends IOException {

	}

	private void showToastForException(Exception e) {
		e.printStackTrace();
		if (e instanceof java.net.UnknownHostException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_server_not_found);
		} else if (e instanceof java.net.ConnectException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_connect);
		} else if (e instanceof WriteException) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_could_not_write_file);
		} else if (!(e instanceof  CancellationException)) {
			mXmppConnectionService.showErrorToastInUi(R.string.download_failed_file_not_found);
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
				HttpDownloadConnection.this.acceptedAutomatically = false;
				HttpDownloadConnection.this.mXmppConnectionService.getNotificationService().push(message);
				return;
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "io exception in http file size checker: " + e.getMessage());
				if (interactive) {
					showToastForException(e);
				}
				cancel();
				return;
			}
			file.setExpectedSize(size);
			if (mHttpConnectionManager.hasStoragePermission() && size <= mHttpConnectionManager.getAutoAcceptFileSize()) {
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
				if (mUseTor) {
					connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mUrl.openConnection();
				}
				connection.setRequestMethod("HEAD");
				Log.d(Config.LOGTAG,"url: "+connection.getURL().toString());
				Log.d(Config.LOGTAG,"connection: "+connection.toString());
				connection.setRequestProperty("User-Agent", mXmppConnectionService.getIqGenerator().getIdentityName());
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
				}
				connection.connect();
				String contentLength = connection.getHeaderField("Content-Length");
				connection.disconnect();
				if (contentLength == null) {
					throw new IOException();
				}
				return Long.parseLong(contentLength, 10);
			} catch (IOException e) {
				throw e;
			} catch (NumberFormatException e) {
				throw new IOException();
			}
		}

	}

	private class FileDownloader implements Runnable {

		private boolean interactive = false;

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
				}
				cancel();
			}
		}

		private void download()  throws Exception {
			InputStream is = null;
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_download_"+message.getUuid());
			try {
				wakeLock.acquire();
				HttpURLConnection connection;
				if (mUseTor) {
					connection = (HttpURLConnection) mUrl.openConnection(mHttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mUrl.openConnection();
				}
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, interactive);
				}
				connection.setRequestProperty("User-Agent",mXmppConnectionService.getIqGenerator().getIdentityName());
				final boolean tryResume = file.exists() && file.getKey() == null;
				if (tryResume) {
					Log.d(Config.LOGTAG,"http download trying resume");
					long size = file.getSize();
					connection.setRequestProperty("Range", "bytes="+size+"-");
				}
				connection.connect();
				is = new BufferedInputStream(connection.getInputStream());
				boolean serverResumed = "bytes".equals(connection.getHeaderField("Accept-Ranges"));
				long transmitted = 0;
				long expected = file.getExpectedSize();
				if (tryResume && serverResumed) {
					Log.d(Config.LOGTAG,"server resumed");
					transmitted = file.getSize();
					updateProgress((int) ((((double) transmitted) / expected) * 100));
					os = AbstractConnectionManager.createAppendedOutputStream(file);
				} else {
					file.getParentFile().mkdirs();
					file.createNewFile();
					os = AbstractConnectionManager.createOutputStream(file, true);
				}
				int count;
				byte[] buffer = new byte[1024];
				while ((count = is.read(buffer)) != -1) {
					transmitted += count;
					try {
						os.write(buffer, 0, count);
					} catch (IOException e) {
						throw new WriteException();
					}
					updateProgress((int) ((((double) transmitted) / expected) * 100));
					if (canceled) {
						throw new CancellationException();
					}
				}
				try {
					os.flush();
				} catch (IOException e) {
					throw new WriteException();
				}
			} catch (CancellationException | IOException e) {
				throw e;
			} finally {
				FileBackend.close(os);
				FileBackend.close(is);
				wakeLock.release();
			}
		}

		private void updateImageBounds() {
			message.setType(Message.TYPE_FILE);
			mXmppConnectionService.getFileBackend().updateFileParams(message, mUrl);
			mXmppConnectionService.updateMessage(message);
		}

	}

	public void updateProgress(int i) {
		this.mProgress = i;
		mXmppConnectionService.updateConversationUi();
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
}
