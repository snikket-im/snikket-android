package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.DownloadableFile;

public abstract class JingleTransport {
	public abstract void connect(final OnTransportConnected callback);

	public abstract void receive(final DownloadableFile file,
			final OnFileTransmissionStatusChanged callback);

	public abstract void send(final DownloadableFile file,
			final OnFileTransmissionStatusChanged callback);

	public abstract void disconnect();

	protected InputStream createInputStream(DownloadableFile file) {
		FileInputStream is;
		try {
			is = new FileInputStream(file);
			if (file.getKey() == null) {
				return is;
			}
		} catch (FileNotFoundException e) {
			return null;
		}
		try {
			IvParameterSpec ips = new IvParameterSpec(file.getIv());
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
			Log.d(Config.LOGTAG, "opening encrypted input stream");
			return new CipherInputStream(is, cipher);
		} catch (InvalidKeyException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (NoSuchPaddingException e) {
			return null;
		} catch (InvalidAlgorithmParameterException e) {
			return null;
		}
	}

	protected OutputStream createOutputStream(DownloadableFile file) {
		FileOutputStream os;
		try {
			os = new FileOutputStream(file);
			if (file.getKey() == null) {
				return os;
			}
		} catch (FileNotFoundException e) {
			return null;
		}
		try {
			IvParameterSpec ips = new IvParameterSpec(file.getIv());
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
			Log.d(Config.LOGTAG, "opening encrypted output stream");
			return new CipherOutputStream(os, cipher);
		} catch (InvalidKeyException e) {
			return null;
		} catch (NoSuchAlgorithmException e) {
			return null;
		} catch (NoSuchPaddingException e) {
			return null;
		} catch (InvalidAlgorithmParameterException e) {
			return null;
		}
	}
}
