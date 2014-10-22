package eu.siacs.conversations.xmpp.jingle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import android.util.Base64;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleInbandTransport extends JingleTransport {

	private Account account;
	private String counterpart;
	private int blockSize;
	private int bufferSize;
	private int seq = 0;
	private String sessionId;

	private boolean established = false;

	private DownloadableFile file;

	private InputStream fileInputStream = null;
	private OutputStream fileOutputStream;
	private long remainingSize;
	private MessageDigest digest;

	private OnFileTransmissionStatusChanged onFileTransmissionStatusChanged;

	private OnIqPacketReceived onAckReceived = new OnIqPacketReceived() {
		@Override
		public void onIqPacketReceived(Account account, IqPacket packet) {
			if (packet.getType() == IqPacket.TYPE_RESULT) {
				sendNextBlock();
			}
		}
	};

	public JingleInbandTransport(Account account, String counterpart,
			String sid, int blocksize) {
		this.account = account;
		this.counterpart = counterpart;
		this.blockSize = blocksize;
		this.bufferSize = blocksize / 4;
		this.sessionId = sid;
	}

	public void connect(final OnTransportConnected callback) {
		IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
		iq.setTo(this.counterpart);
		Element open = iq.addChild("open", "http://jabber.org/protocol/ibb");
		open.setAttribute("sid", this.sessionId);
		open.setAttribute("stanza", "iq");
		open.setAttribute("block-size", Integer.toString(this.blockSize));

		this.account.getXmppConnection().sendIqPacket(iq,
				new OnIqPacketReceived() {

					@Override
					public void onIqPacketReceived(Account account,
							IqPacket packet) {
						if (packet.getType() == IqPacket.TYPE_ERROR) {
							callback.failed();
						} else {
							callback.established();
						}
					}
				});
	}

	@Override
	public void receive(DownloadableFile file,
			OnFileTransmissionStatusChanged callback) {
		this.onFileTransmissionStatusChanged = callback;
		this.file = file;
		try {
			this.digest = MessageDigest.getInstance("SHA-1");
			digest.reset();
			file.getParentFile().mkdirs();
			file.createNewFile();
			this.fileOutputStream = file.createOutputStream();
			if (this.fileOutputStream == null) {
				callback.onFileTransferAborted();
				return;
			}
			this.remainingSize = file.getExpectedSize();
		} catch (NoSuchAlgorithmException e) {
			callback.onFileTransferAborted();
		} catch (IOException e) {
			callback.onFileTransferAborted();
		}
	}

	@Override
	public void send(DownloadableFile file,
			OnFileTransmissionStatusChanged callback) {
		this.onFileTransmissionStatusChanged = callback;
		this.file = file;
		try {
			this.digest = MessageDigest.getInstance("SHA-1");
			this.digest.reset();
			fileInputStream = this.file.createInputStream();
			if (fileInputStream == null) {
				callback.onFileTransferAborted();
				return;
			}
			this.sendNextBlock();
		} catch (NoSuchAlgorithmException e) {
			callback.onFileTransferAborted();
		}
	}

	private void sendNextBlock() {
		byte[] buffer = new byte[this.bufferSize];
		try {
			int count = fileInputStream.read(buffer);
			if (count == -1) {
				file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
				fileInputStream.close();
				this.onFileTransmissionStatusChanged.onFileTransmitted(file);
			} else {
				this.digest.update(buffer);
				String base64 = Base64.encodeToString(buffer, Base64.NO_WRAP);
				IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
				iq.setTo(this.counterpart);
				Element data = iq.addChild("data",
						"http://jabber.org/protocol/ibb");
				data.setAttribute("seq", Integer.toString(this.seq));
				data.setAttribute("block-size",
						Integer.toString(this.blockSize));
				data.setAttribute("sid", this.sessionId);
				data.setContent(base64);
				this.account.getXmppConnection().sendIqPacket(iq,
						this.onAckReceived);
				this.seq++;
			}
		} catch (IOException e) {
			this.onFileTransmissionStatusChanged.onFileTransferAborted();
		}
	}

	private void receiveNextBlock(String data) {
		try {
			byte[] buffer = Base64.decode(data, Base64.NO_WRAP);
			if (this.remainingSize < buffer.length) {
				buffer = Arrays
						.copyOfRange(buffer, 0, (int) this.remainingSize);
			}
			this.remainingSize -= buffer.length;

			this.fileOutputStream.write(buffer);

			this.digest.update(buffer);
			if (this.remainingSize <= 0) {
				file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
				fileOutputStream.flush();
				fileOutputStream.close();
				this.onFileTransmissionStatusChanged.onFileTransmitted(file);
			}
		} catch (IOException e) {
			this.onFileTransmissionStatusChanged.onFileTransferAborted();
		}
	}

	public void deliverPayload(IqPacket packet, Element payload) {
		if (payload.getName().equals("open")) {
			if (!established) {
				established = true;
				this.account.getXmppConnection().sendIqPacket(
						packet.generateRespone(IqPacket.TYPE_RESULT), null);
			} else {
				this.account.getXmppConnection().sendIqPacket(
						packet.generateRespone(IqPacket.TYPE_ERROR), null);
			}
		} else if (payload.getName().equals("data")) {
			this.receiveNextBlock(payload.getContent());
			this.account.getXmppConnection().sendIqPacket(
					packet.generateRespone(IqPacket.TYPE_RESULT), null);
		} else {
			// TODO some sort of exception
		}
	}
}
