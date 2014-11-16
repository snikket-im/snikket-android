package eu.siacs.conversations.utils;

import java.security.SecureRandom;
import java.text.Normalizer;

public class CryptoHelper {
	public static final String FILETRANSFER = "?FILETRANSFERv1:";
	final protected static char[] hexArray = "0123456789abcdef".toCharArray();
	final protected static char[] vowels = "aeiou".toCharArray();
	final protected static char[] consonants = "bcdfghjklmnpqrstvwxyz".toCharArray();
	final public static byte[] ONE = new byte[] { 0, 0, 0, 1 };

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public static byte[] hexToBytes(String hexString) {
		int len = hexString.length();
		byte[] array = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			array[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character
					.digit(hexString.charAt(i + 1), 16));
		}
		return array;
	}

	public static String hexToString(final String hexString) {
		return new String(hexToBytes(hexString));
	}

	public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	public static String randomMucName(SecureRandom random) {
		return randomWord(3, random) + "." + randomWord(7, random);
	}

	protected static String randomWord(int lenght, SecureRandom random) {
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

	/**
	 * Escapes usernames or passwords for SASL.
	 */
	public static String saslEscape(final String s) {
		final StringBuilder sb = new StringBuilder((int) (s.length() * 1.1));
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case ',':
					sb.append("=2C");
					break;
				case '=':
					sb.append("=3D");
					break;
				default:
					sb.append(c);
					break;
			}
		}
		return sb.toString();
	}

	public static String saslPrep(final String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKC);
	}

	public static String prettifyFingerprint(String fingerprint) {
		StringBuilder builder = new StringBuilder(fingerprint);
		builder.insert(8, " ");
		builder.insert(17, " ");
		builder.insert(26, " ");
		builder.insert(35, " ");
		return builder.toString();
	}
}
