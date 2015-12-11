package eu.siacs.conversations.services;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.util.Pair;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

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

public class AbstractConnectionManager {
	protected XmppConnectionService mXmppConnectionService;

	public AbstractConnectionManager(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public XmppConnectionService getXmppConnectionService() {
		return this.mXmppConnectionService;
	}

	public long getAutoAcceptFileSize() {
		String config = this.mXmppConnectionService.getPreferences().getString(
				"auto_accept_file_size", "524288");
		try {
			return Long.parseLong(config);
		} catch (NumberFormatException e) {
			return 524288;
		}
	}

	public boolean hasStoragePermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			return mXmppConnectionService.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
		} else {
			return true;
		}
	}

	public static Pair<InputStream,Integer> createInputStream(DownloadableFile file, boolean gcm) throws FileNotFoundException {
		FileInputStream is;
		int size;
		is = new FileInputStream(file);
		size = (int) file.getSize();
		if (file.getKey() == null) {
			return new Pair<InputStream,Integer>(is,size);
		}
		try {
			if (gcm) {
				AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
				cipher.init(true, new AEADParameters(new KeyParameter(file.getKey()), 128, file.getIv()));
				InputStream cis = new org.bouncycastle.crypto.io.CipherInputStream(is, cipher);
				return new Pair<>(cis, cipher.getOutputSize(size));
			} else {
				IvParameterSpec ips = new IvParameterSpec(file.getIv());
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
				Log.d(Config.LOGTAG, "opening encrypted input stream");
				final int s = Config.REPORT_WRONG_FILESIZE_IN_OTR_JINGLE ? size : (size / 16 + 1) * 16;
				return new Pair<InputStream,Integer>(new CipherInputStream(is, cipher),s);
			}
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

	public static OutputStream createAppendedOutputStream(DownloadableFile file) {
		return createOutputStream(file, false, true);
	}

	public static OutputStream createOutputStream(DownloadableFile file, boolean gcm) {
		return createOutputStream(file, gcm, false);
	}

	private static OutputStream createOutputStream(DownloadableFile file, boolean gcm, boolean append) {
		FileOutputStream os;
		try {
			os = new FileOutputStream(file, append);
			if (file.getKey() == null) {
				return os;
			}
		} catch (FileNotFoundException e) {
			return null;
		}
		try {
			if (gcm) {
				AEADBlockCipher cipher = new GCMBlockCipher(new AESEngine());
				cipher.init(false, new AEADParameters(new KeyParameter(file.getKey()), 128, file.getIv()));
				return new org.bouncycastle.crypto.io.CipherOutputStream(os, cipher);
			} else {
				IvParameterSpec ips = new IvParameterSpec(file.getIv());
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(file.getKey(), "AES"), ips);
				Log.d(Config.LOGTAG, "opening encrypted output stream");
				return new CipherOutputStream(os, cipher);
			}
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

	public PowerManager.WakeLock createWakeLock(String name) {
		PowerManager powerManager = (PowerManager) mXmppConnectionService.getSystemService(Context.POWER_SERVICE);
		return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,name);
	}
}
