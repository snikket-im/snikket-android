package eu.siacs.conversations.utils;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import eu.siacs.conversations.Config;

public final class CryptoHelper {
	public static final String FILETRANSFER = "?FILETRANSFERv1:";
	private final static char[] hexArray = "0123456789abcdef".toCharArray();
	private final static char[] vowels = "aeiou".toCharArray();
	private final static char[] consonants = "bcdfghjklmnpqrstvwxyz".toCharArray();
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

	private static String randomWord(int lenght, SecureRandom random) {
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
		if (fingerprint==null) {
			return "";
		} else if (fingerprint.length() < 40) {
			return fingerprint;
		}
		StringBuilder builder = new StringBuilder(fingerprint.replaceAll("\\s",""));
		for(int i=8;i<builder.length();i+=9) {
			builder.insert(i, ' ');
		}
		return builder.toString();
	}

	public static String[] getOrderedCipherSuites(final String[] platformSupportedCipherSuites) {
		final Collection<String> cipherSuites = new LinkedHashSet<>(Arrays.asList(Config.ENABLED_CIPHERS));
		final List<String> platformCiphers = Arrays.asList(platformSupportedCipherSuites);
		cipherSuites.retainAll(platformCiphers);
		cipherSuites.addAll(platformCiphers);
		filterWeakCipherSuites(cipherSuites);
		return cipherSuites.toArray(new String[cipherSuites.size()]);
	}

	private static void filterWeakCipherSuites(final Collection<String> cipherSuites) {
		final Iterator<String> it = cipherSuites.iterator();
		while (it.hasNext()) {
			String cipherName = it.next();
			// remove all ciphers with no or very weak encryption or no authentication
			for (String weakCipherPattern : Config.WEAK_CIPHER_PATTERNS) {
				if (cipherName.contains(weakCipherPattern)) {
					it.remove();
					break;
				}
			}
		}
	}
}
