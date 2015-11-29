package eu.siacs.conversations.xmpp.jingle;

import android.os.PowerManager;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.SocksSocketFactory;

public class JingleSocks5Transport extends JingleTransport {
	private JingleCandidate candidate;
	private JingleConnection connection;
	private String destination;
	private OutputStream outputStream;
	private InputStream inputStream;
	private boolean isEstablished = false;
	private boolean activated = false;
	protected Socket socket;

	public JingleSocks5Transport(JingleConnection jingleConnection,
			JingleCandidate candidate) {
		this.candidate = candidate;
		this.connection = jingleConnection;
		try {
			MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
			StringBuilder destBuilder = new StringBuilder();
			destBuilder.append(jingleConnection.getSessionId());
			if (candidate.isOurs()) {
				destBuilder.append(jingleConnection.getAccount().getJid());
				destBuilder.append(jingleConnection.getCounterPart());
			} else {
				destBuilder.append(jingleConnection.getCounterPart());
				destBuilder.append(jingleConnection.getAccount().getJid());
			}
			mDigest.reset();
			this.destination = CryptoHelper.bytesToHex(mDigest
					.digest(destBuilder.toString().getBytes()));
		} catch (NoSuchAlgorithmException e) {

		}
	}

	public void connect(final OnTransportConnected callback) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					final boolean useTor = connection.getAccount().isOnion() || connection.getConnectionManager().getXmppConnectionService().useTorToConnect();
					if (useTor) {
						socket = SocksSocketFactory.createSocketOverTor(candidate.getHost(),candidate.getPort());
					} else {
						socket = new Socket();
						SocketAddress address = new InetSocketAddress(candidate.getHost(),candidate.getPort());
						socket.connect(address,Config.SOCKET_TIMEOUT * 1000);
					}
					inputStream = socket.getInputStream();
					outputStream = socket.getOutputStream();
					SocksSocketFactory.createSocksConnection(socket,destination,0);
					isEstablished = true;
					callback.established();
				} catch (UnknownHostException e) {
					callback.failed();
				} catch (IOException e) {
					callback.failed();
				}
			}
		}).start();

	}

	public void send(final DownloadableFile file, final OnFileTransmissionStatusChanged callback) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				InputStream fileInputStream = null;
				final PowerManager.WakeLock wakeLock = connection.getConnectionManager().createWakeLock("jingle_send_"+connection.getSessionId());
				try {
					wakeLock.acquire();
					MessageDigest digest = MessageDigest.getInstance("SHA-1");
					digest.reset();
					fileInputStream = connection.getFileInputStream();
					if (fileInputStream == null) {
						Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": could not create input stream");
						callback.onFileTransferAborted();
						return;
					}
					long size = file.getExpectedSize();
					long transmitted = 0;
					int count;
					byte[] buffer = new byte[8192];
					while ((count = fileInputStream.read(buffer)) > 0) {
						outputStream.write(buffer, 0, count);
						digest.update(buffer, 0, count);
						transmitted += count;
						connection.updateProgress((int) ((((double) transmitted) / size) * 100));
					}
					outputStream.flush();
					file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
					if (callback != null) {
						callback.onFileTransmitted(file);
					}
				} catch (FileNotFoundException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} catch (IOException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} catch (NoSuchAlgorithmException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} finally {
					FileBackend.close(fileInputStream);
					wakeLock.release();
				}
			}
		}).start();

	}

	public void receive(final DownloadableFile file, final OnFileTransmissionStatusChanged callback) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				OutputStream fileOutputStream = null;
				final PowerManager.WakeLock wakeLock = connection.getConnectionManager().createWakeLock("jingle_receive_"+connection.getSessionId());
				try {
					wakeLock.acquire();
					MessageDigest digest = MessageDigest.getInstance("SHA-1");
					digest.reset();
					//inputStream.skip(45);
					socket.setSoTimeout(30000);
					file.getParentFile().mkdirs();
					file.createNewFile();
					fileOutputStream = connection.getFileOutputStream();
					if (fileOutputStream == null) {
						callback.onFileTransferAborted();
						Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": could not create output stream");
						return;
					}
					double size = file.getExpectedSize();
					long remainingSize = file.getExpectedSize();
					byte[] buffer = new byte[8192];
					int count;
					while (remainingSize > 0) {
						count = inputStream.read(buffer);
						if (count == -1) {
							callback.onFileTransferAborted();
							Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": file ended prematurely with "+remainingSize+" bytes remaining");
							return;
						} else {
							fileOutputStream.write(buffer, 0, count);
							digest.update(buffer, 0, count);
							remainingSize -= count;
						}
						connection.updateProgress((int) (((size - remainingSize) / size) * 100));
					}
					fileOutputStream.flush();
					fileOutputStream.close();
					file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
					callback.onFileTransmitted(file);
				} catch (FileNotFoundException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} catch (IOException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} catch (NoSuchAlgorithmException e) {
					Log.d(Config.LOGTAG, connection.getAccount().getJid().toBareJid() + ": "+e.getMessage());
					callback.onFileTransferAborted();
				} finally {
					wakeLock.release();
					FileBackend.close(fileOutputStream);
					FileBackend.close(inputStream);
				}
			}
		}).start();
	}

	public boolean isProxy() {
		return this.candidate.getType() == JingleCandidate.TYPE_PROXY;
	}

	public boolean needsActivation() {
		return (this.isProxy() && !this.activated);
	}

	public void disconnect() {
		FileBackend.close(inputStream);
		FileBackend.close(outputStream);
		FileBackend.close(socket);
	}

	public boolean isEstablished() {
		return this.isEstablished;
	}

	public JingleCandidate getCandidate() {
		return this.candidate;
	}

	public void setActivated(boolean activated) {
		this.activated = activated;
	}
}
