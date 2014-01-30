package de.gultsch.chat.utils;

import android.util.Base64;

public class SASL {
	public static String plain(String username, String password) {
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
}
