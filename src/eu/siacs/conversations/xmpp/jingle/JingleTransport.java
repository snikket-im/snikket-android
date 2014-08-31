package eu.siacs.conversations.xmpp.jingle;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

import eu.siacs.conversations.Config;

import android.util.Log;

public abstract class JingleTransport {
	public abstract void connect(final OnTransportConnected callback);

	public abstract void receive(final JingleFile file,
			final OnFileTransmissionStatusChanged callback);

	public abstract void send(final JingleFile file,
			final OnFileTransmissionStatusChanged callback);

	private byte[] iv = { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
			0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0xf };

	protected InputStream getInputStream(JingleFile file)
			throws FileNotFoundException {
		if (file.getKey() == null) {
			return new FileInputStream(file);
		} else {
			try {
				IvParameterSpec ips = new IvParameterSpec(iv);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, file.getKey(), ips);
				Log.d(Config.LOGTAG, "opening encrypted input stream");
				return new CipherInputStream(new FileInputStream(file), cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d(Config.LOGTAG, "no such algo: " + e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d(Config.LOGTAG, "no such padding: " + e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, "invalid key: " + e.getMessage());
				return null;
			} catch (InvalidAlgorithmParameterException e) {
				Log.d(Config.LOGTAG, "invavid iv:" + e.getMessage());
				return null;
			}
		}
	}

	protected OutputStream getOutputStream(JingleFile file)
			throws FileNotFoundException {
		if (file.getKey() == null) {
			return new FileOutputStream(file);
		} else {
			try {
				IvParameterSpec ips = new IvParameterSpec(iv);
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, file.getKey(), ips);
				Log.d(Config.LOGTAG, "opening encrypted output stream");
				return new CipherOutputStream(new FileOutputStream(file),
						cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d(Config.LOGTAG, "no such algo: " + e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d(Config.LOGTAG, "no such padding: " + e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d(Config.LOGTAG, "invalid key: " + e.getMessage());
				return null;
			} catch (InvalidAlgorithmParameterException e) {
				Log.d(Config.LOGTAG, "invavid iv:" + e.getMessage());
				return null;
			}
		}
	}
}
