package eu.siacs.conversations.utils;

import java.security.SecureRandom;
import java.util.Random;

import android.util.Base64;

public class CryptoHelper {
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
	final protected static char[] vowels = "aeiou".toCharArray();
	final protected static char[] consonants ="bcdfghjklmnpqrstvwxyz".toCharArray();
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	public static String saslPlain(String username, String password) {
		byte[] userBytes = username.getBytes();
		int userLenght = userBytes.length;
		byte[] passwordBytes = password.getBytes();
		byte[] saslBytes = new byte[userBytes.length+passwordBytes.length+2];
		saslBytes[0] = 0x0;
		for(int i = 1; i < saslBytes.length; ++i) {
			if (i<=userLenght) {
				saslBytes[i] = userBytes[i-1];
			} else if (i==userLenght+1) {
				saslBytes[i] = 0x0;
			} else {
				saslBytes[i] = passwordBytes[i-(userLenght+2)];
			}
		}
		
		return Base64.encodeToString(saslBytes, Base64.DEFAULT);
	}
	
	public static String randomMucName() {
		Random random = new SecureRandom();
		return randomWord(3,random)+"."+randomWord(7,random);
	}
	
	protected static String randomWord(int lenght,Random random) {
		StringBuilder builder = new StringBuilder(lenght);
		for(int i = 0; i < lenght; ++i) {
			if (i % 2 == 0) {
				builder.append(consonants[random.nextInt(consonants.length)]);
			} else {
				builder.append(vowels[random.nextInt(vowels.length)]);
			}
		}
		return builder.toString();
	}
}
