package eu.siacs.conversations.http;

import android.app.PendingIntent;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class HttpUploadConnection implements Transferable {

	private HttpConnectionManager mHttpConnectionManager;
	private XmppConnectionService mXmppConnectionService;

	private boolean canceled = false;
	private boolean delayed = false;
	private Account account;
	private DownloadableFile file;
	private Message message;
	private String mime;
	private URL mGetUrl;
	private URL mPutUrl;
	private boolean mUseTor = false;

	private byte[] key = null;

	private long transmitted = 0;

	private InputStream mFileInputStream;

	public HttpUploadConnection(HttpConnectionManager httpConnectionManager) {
		this.mHttpConnectionManager = httpConnectionManager;
		this.mXmppConnectionService = httpConnectionManager.getXmppConnectionService();
		this.mUseTor = mXmppConnectionService.useTorToConnect();
	}

	@Override
	public boolean start() {
		return false;
	}

	@Override
	public int getStatus() {
		return STATUS_UPLOADING;
	}

	@Override
	public long getFileSize() {
		return file == null ? 0 : file.getExpectedSize();
	}

	@Override
	public int getProgress() {
		if (file == null) {
			return 0;
		}
		return (int) ((((double) transmitted) / file.getExpectedSize()) * 100);
	}

	@Override
	public void cancel() {
		this.canceled = true;
	}

	private void fail() {
		mHttpConnectionManager.finishUploadConnection(this);
		message.setTransferable(null);
		mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
		FileBackend.close(mFileInputStream);
	}

	public void init(Message message, boolean delay) {
		this.message = message;
		this.account = message.getConversation().getAccount();
		this.file = mXmppConnectionService.getFileBackend().getFile(message, false);
		this.mime = this.file.getMimeType();
		this.delayed = delay;
		if (Config.ENCRYPT_ON_HTTP_UPLOADED
				|| message.getEncryption() == Message.ENCRYPTION_AXOLOTL
				|| message.getEncryption() == Message.ENCRYPTION_OTR) {
			this.key = new byte[48];
			mXmppConnectionService.getRNG().nextBytes(this.key);
			this.file.setKeyAndIv(this.key);
		}
		Pair<InputStream,Integer> pair;
		try {
			pair = AbstractConnectionManager.createInputStream(file, true);
		} catch (FileNotFoundException e) {
			fail();
			return;
		}
		this.file.setExpectedSize(pair.second);
		this.mFileInputStream = pair.first;
		Jid host = account.getXmppConnection().findDiscoItemByFeature(Xmlns.HTTP_UPLOAD);
		IqPacket request = mXmppConnectionService.getIqGenerator().requestHttpUploadSlot(host,file,mime);
		mXmppConnectionService.sendIqPacket(account, request, new OnIqPacketReceived() {
			@Override
			public void onIqPacketReceived(Account account, IqPacket packet) {
				if (packet.getType() == IqPacket.TYPE.RESULT) {
					Element slot = packet.findChild("slot",Xmlns.HTTP_UPLOAD);
					if (slot != null) {
						try {
							mGetUrl = new URL(slot.findChildContent("get"));
							mPutUrl = new URL(slot.findChildContent("put"));
							if (!canceled) {
								new Thread(new FileUploader()).start();
							}
						} catch (MalformedURLException e) {
							fail();
						}
					} else {
						fail();
					}
				} else {
					fail();
				}
			}
		});
		message.setTransferable(this);
		mXmppConnectionService.markMessage(message, Message.STATUS_UNSEND);
	}

	private class FileUploader implements Runnable {

		@Override
		public void run() {
			this.upload();
		}

		private void upload() {
			OutputStream os = null;
			HttpURLConnection connection = null;
			PowerManager.WakeLock wakeLock = mHttpConnectionManager.createWakeLock("http_upload_"+message.getUuid());
			try {
				wakeLock.acquire();
				Log.d(Config.LOGTAG, "uploading to " + mPutUrl.toString());
				if (mUseTor) {
					connection = (HttpURLConnection) mPutUrl.openConnection(mHttpConnectionManager.getProxy());
				} else {
					connection = (HttpURLConnection) mPutUrl.openConnection();
				}
				if (connection instanceof HttpsURLConnection) {
					mHttpConnectionManager.setupTrustManager((HttpsURLConnection) connection, true);
				}
				connection.setRequestMethod("PUT");
				connection.setFixedLengthStreamingMode((int) file.getExpectedSize());
				connection.setRequestProperty("Content-Type", mime == null ? "application/octet-stream" : mime);
				connection.setRequestProperty("User-Agent",mXmppConnectionService.getIqGenerator().getIdentityName());
				connection.setDoOutput(true);
				connection.connect();
				os = connection.getOutputStream();
				transmitted = 0;
				int count = -1;
				byte[] buffer = new byte[4096];
				while (((count = mFileInputStream.read(buffer)) != -1) && !canceled) {
					transmitted += count;
					os.write(buffer, 0, count);
					mXmppConnectionService.updateConversationUi();
				}
				os.flush();
				os.close();
				mFileInputStream.close();
				int code = connection.getResponseCode();
				if (code == 200 || code == 201) {
					Log.d(Config.LOGTAG, "finished uploading file");
					if (key != null) {
						mGetUrl = new URL(mGetUrl.toString() + "#" + CryptoHelper.bytesToHex(key));
					}
					mXmppConnectionService.getFileBackend().updateFileParams(message, mGetUrl);
					mXmppConnectionService.getFileBackend().updateMediaScanner(file);
					message.setTransferable(null);
					message.setCounterpart(message.getConversation().getJid().toBareJid());
					if (message.getEncryption() == Message.ENCRYPTION_DECRYPTED) {
						mXmppConnectionService.getPgpEngine().encrypt(message, new UiCallback<Message>() {
							@Override
							public void success(Message message) {
								mXmppConnectionService.resendMessage(message,delayed);
							}

							@Override
							public void error(int errorCode, Message object) {
								fail();
							}

							@Override
							public void userInputRequried(PendingIntent pi, Message object) {
								fail();
							}
						});
					} else {
						mXmppConnectionService.resendMessage(message, delayed);
					}
				} else {
					Log.d(Config.LOGTAG,"http upload failed because response code was "+code);
					fail();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(Config.LOGTAG,"http upload failed "+e.getMessage());
				fail();
			} finally {
				FileBackend.close(mFileInputStream);
				FileBackend.close(os);
				if (connection != null) {
					connection.disconnect();
				}
				wakeLock.release();
			}
		}
	}
}
