package eu.siacs.conversations.xmpp.jingle;

import java.io.File;
import java.security.Key;

import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.utils.CryptoHelper;
import android.util.Log;

public class JingleFile extends File {
	
	private static final long serialVersionUID = 2247012619505115863L;
	
	private long expectedSize = 0;
	private String sha1sum;
	private Key aeskey;
	
	public JingleFile(String path) {
		super(path);
	}
	
	public long getSize() {
		return super.length();
	}
	
	public long getExpectedSize() {
		if (this.aeskey!=null) {
			return (this.expectedSize/16 + 1) * 16;
		} else {
			return this.expectedSize;
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
	
	public void setKey(byte[] key) {
		if (key.length>=32) {
			byte[] secretKey = new byte[32];
			System.arraycopy(key, 0, secretKey, 0, 32);
			this.aeskey = new SecretKeySpec(secretKey, "AES");
		} else if (key.length>=16) {
			byte[] secretKey = new byte[16];
			System.arraycopy(key, 0, secretKey, 0, 16);
			this.aeskey = new SecretKeySpec(secretKey, "AES");
		} else {
			Log.d("xmppService","weird key");
		}
		Log.d("xmppService","using aes key "+CryptoHelper.bytesToHex(this.aeskey.getEncoded()));
	}
	
	public Key getKey() {
		return this.aeskey;
	}
}
