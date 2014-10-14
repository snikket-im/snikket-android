package eu.siacs.conversations.http;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;

public class HttpConnection implements Downloadable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private URL mUrl;
	private Message message;
	private DownloadableFile file;

	public HttpConnection(HttpConnectionManager manager) {
		this.mHttpConnectionManager = manager;
		this.mXmppConnectionService = manager.getXmppConnectionService();
	}

	@Override
	public void start() {
		new Thread(new FileDownloader()).start();
	}

	public void init(Message message) {
		this.message = message;
		this.message.setDownloadable(this);
		try {
			mUrl = new URL(message.getBody());
			this.file = mXmppConnectionService.getFileBackend().getConversationsFile(message,false);
			message.setType(Message.TYPE_IMAGE);
			mXmppConnectionService.markMessage(message, Message.STATUS_RECEIVED_OFFER);
			checkFileSize();
		} catch (MalformedURLException e) {
			this.cancel();
		}
	}
	
	private void checkFileSize() {
		new Thread(new FileSizeChecker()).start();
	}

	public void cancel() {
		mXmppConnectionService.markMessage(message, Message.STATUS_RECEPTION_FAILED);
		Log.d(Config.LOGTAG,"canceled download");
	}

	private class FileSizeChecker implements Runnable {

		@Override
		public void run() {
			try {
				long size = retrieveFileSize();
				file.setExpectedSize(size);
				if (size <= mHttpConnectionManager.getAutoAcceptFileSize()) {
					start();
				}
				Log.d(Config.LOGTAG,"file size: "+size);
			} catch (IOException e) {
				cancel();
			}
		}

		private long retrieveFileSize() throws IOException {
			HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
			connection.setRequestMethod("HEAD");
			if (connection instanceof HttpsURLConnection) {
				
			}
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

		@Override
		public void run() {
			try {
				mXmppConnectionService.markMessage(message, Message.STATUS_RECEIVING);
				download();
				mXmppConnectionService.markMessage(message, Message.STATUS_RECEIVED);
			} catch (IOException e) {
				cancel();
			}
		}
		
		private void download() throws IOException {
			HttpURLConnection connection = (HttpURLConnection) mUrl.openConnection();
			if (connection instanceof HttpsURLConnection) {
				
			}
			BufferedInputStream is = new BufferedInputStream(connection.getInputStream());
			OutputStream os = file.createOutputStream();
			int count = -1;
			byte[] buffer = new byte[1024];
			while ((count = is.read(buffer)) != -1) {
				os.write(buffer, 0, count);
			}
			os.flush();
			os.close();
			is.close();
			Log.d(Config.LOGTAG,"finished downloading "+file.getAbsolutePath().toString());
		}
		
	}
}