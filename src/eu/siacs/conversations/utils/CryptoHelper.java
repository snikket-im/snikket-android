package eu.siacs.conversations.utils;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import eu.siacs.conversations.entities.Account;

import android.util.Base64;
import android.util.Log;

public class CryptoHelper {
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	final protected static char[] vowels = "aeiou".toCharArray();
	final protected static char[] consonants = "bcdfghjklmnpqrstvwxyz"
			.toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars).toLowerCase();
	}

	public static String saslPlain(String username, String password) {
		String sasl = '\u0000' + username + '\u0000' + password;
		return Base64.encodeToString(sasl.getBytes(Charset.defaultCharset()),
				Base64.NO_WRAP);
	}

	private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
	    byte[] result = new byte[a.length + b.length]; 
	    System.arraycopy(a, 0, result, 0, a.length); 
	    System.arraycopy(b, 0, result, a.length, b.length); 
	    return result;
	} 
	
	public static String saslDigestMd5(Account account, String challenge) {
		try {
			Random random = new SecureRandom();
			String[] challengeParts = new String(Base64.decode(challenge,
					Base64.DEFAULT)).split(",");
			String nonce = "";
			for (int i = 0; i < challengeParts.length; ++i) {
				String[] parts = challengeParts[i].split("=");
				if (parts[0].equals("nonce")) {
					nonce = parts[1].replace("\"", "");
				} else if (parts[0].equals("rspauth")) {
					return null;
				}
			}
			String digestUri = "xmpp/"+account.getServer();
			String nonceCount = "00000001";
			String x = account.getUsername() + ":" + account.getServer() + ":"
					+ account.getPassword();
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] y = md
					.digest(x.getBytes(Charset.defaultCharset()));
			String cNonce = new BigInteger(100, random).toString(32);
			byte[] a1 = concatenateByteArrays(y,(":"+nonce+":"+cNonce).getBytes(Charset.defaultCharset()));
			String a2 = "AUTHENTICATE:"+digestUri;
			String ha1 = bytesToHex(md.digest(a1));
			String ha2 = bytesToHex(md.digest(a2.getBytes(Charset
					.defaultCharset())));
			String kd = ha1 + ":" + nonce + ":"+nonceCount+":" + cNonce + ":auth:"
					+ ha2;
			String response = bytesToHex(md.digest(kd.getBytes(Charset
					.defaultCharset())));
			String saslString = "username=\"" + account.getUsername()
					+ "\",realm=\"" + account.getServer() + "\",nonce=\""
					+ nonce + "\",cnonce=\"" + cNonce
					+ "\",nc="+nonceCount+",qop=auth,digest-uri=\""+digestUri+"\",response=" + response
					+ ",charset=utf-8";
			return Base64.encodeToString(
					saslString.getBytes(Charset.defaultCharset()),
					Base64.NO_WRAP);
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	public static String randomMucName() {
		Random random = new SecureRandom();
		return randomWord(3, random) + "." + randomWord(7, random);
	}

	protected static String randomWord(int lenght, Random random) {
		StringBuilder builder = new StringBuilder(lenght);
		for (int i = 0; i < lenght; ++i) {
			if (i % 2 == 0) {
				builder.append(consonants[random.nextInt(consonants.length)]);
			} else {
				builder.append(vowels[random.nextInt(vowels.length)]);
			}
		}
		return builder.toString();
	}
}
