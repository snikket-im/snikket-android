package eu.siacs.conversations.entities;

import java.io.File;

import eu.siacs.conversations.utils.MimeUtils;

public class DownloadableFile extends File {

	private static final long serialVersionUID = 2247012619505115863L;

	private long expectedSize = 0;
	private String sha1sum;
	private byte[] aeskey;

	private byte[] iv = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
			0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0xf };

	public DownloadableFile(String path) {
		super(path);
	}

	public long getSize() {
		return super.length();
	}

	public long getExpectedSize() {
		return this.expectedSize;
	}

	public String getMimeType() {
		String path = this.getAbsolutePath();
		int start = path.lastIndexOf('.') + 1;
		if (start < path.length()) {
			String mime = MimeUtils.guessMimeTypeFromExtension(path.substring(start));
			return mime == null ? "" : mime;
		} else {
			return "";
		}
	}

	public void setExpectedSize(long size) {
		this.expectedSize = size;
	}

	public String getSha1Sum() {
		return this.sha1sum;
	}

	public void setSha1Sum(String sum) {
		this.sha1sum = sum;
	}

	public void setKeyAndIv(byte[] keyIvCombo) {
		if (keyIvCombo.length == 48) {
			this.aeskey = new byte[32];
			this.iv = new byte[16];
			System.arraycopy(keyIvCombo, 0, this.iv, 0, 16);
			System.arraycopy(keyIvCombo, 16, this.aeskey, 0, 32);
		} else if (keyIvCombo.length >= 32) {
			this.aeskey = new byte[32];
			System.arraycopy(keyIvCombo, 0, aeskey, 0, 32);
		} else if (keyIvCombo.length >= 16) {
			this.aeskey = new byte[16];
			System.arraycopy(keyIvCombo, 0, this.aeskey, 0, 16);
		}
	}

	public void setKey(byte[] key) {
		this.aeskey = key;
	}

	public void setIv(byte[] iv) {
		this.iv = iv;
	}

	public byte[] getKey() {
		return this.aeskey;
	}

	public byte[] getIv() {
		return this.iv;
	}
}
