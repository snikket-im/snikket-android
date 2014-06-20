package eu.siacs.conversations.xmpp.jingle;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;

import android.util.Log;

public abstract class JingleTransport {
	public abstract void connect(final OnTransportConnected callback);
	public abstract void receive(final JingleFile file, final OnFileTransmitted callback);
	public abstract void send(final JingleFile file, final OnFileTransmitted callback);
	
	protected InputStream getInputStream(JingleFile file) throws FileNotFoundException {
		if (file.getKey() == null) {
			return new FileInputStream(file);
		} else {
			try {
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, file.getKey());
				Log.d("xmppService","opening encrypted input stream");
				return new CipherInputStream(new FileInputStream(file), cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d("xmppService","no such algo: "+e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d("xmppService","no such padding: "+e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d("xmppService","invalid key: "+e.getMessage());
				return null;
			}
		}
	}
	
	protected OutputStream getOutputStream(JingleFile file) throws FileNotFoundException {
		if (file.getKey() == null) {
			return new FileOutputStream(file);
		} else {
			try {
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, file.getKey());
				Log.d("xmppService","opening encrypted output stream");
				return new CipherOutputStream(new FileOutputStream(file), cipher);
			} catch (NoSuchAlgorithmException e) {
				Log.d("xmppService","no such algo: "+e.getMessage());
				return null;
			} catch (NoSuchPaddingException e) {
				Log.d("xmppService","no such padding: "+e.getMessage());
				return null;
			} catch (InvalidKeyException e) {
				Log.d("xmppService","invalid key: "+e.getMessage());
				return null;
			}
		}
	}
}
