package eu.siacs.conversations.crypto.axolotl;

import android.util.Base64;
import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

public class XmppAxolotlMessage {
	public static final String CONTAINERTAG = "encrypted";
	public static final String HEADER = "header";
	public static final String SOURCEID = "sid";
	public static final String KEYTAG = "key";
	public static final String REMOTEID = "rid";
	public static final String IVTAG = "iv";
	public static final String PAYLOAD = "payload";

	private static final String KEYTYPE = "AES";
	private static final String CIPHERMODE = "AES/GCM/NoPadding";
	private static final String PROVIDER = "BC";

	private byte[] innerKey;
	private byte[] ciphertext = null;
	private byte[] iv = null;
	private final Map<Integer, byte[]> keys;
	private final Jid from;
	private final int sourceDeviceId;

	public static class XmppAxolotlPlaintextMessage {
		private final String plaintext;
		private final String fingerprint;

		public XmppAxolotlPlaintextMessage(String plaintext, String fingerprint) {
			this.plaintext = plaintext;
			this.fingerprint = fingerprint;
		}

		public String getPlaintext() {
			return plaintext;
		}


		public String getFingerprint() {
			return fingerprint;
		}
	}

	public static class XmppAxolotlKeyTransportMessage {
		private final String fingerprint;
		private final byte[] key;
		private final byte[] iv;

		public XmppAxolotlKeyTransportMessage(String fingerprint, byte[] key, byte[] iv) {
			this.fingerprint = fingerprint;
			this.key = key;
			this.iv = iv;
		}

		public String getFingerprint() {
			return fingerprint;
		}

		public byte[] getKey() {
			return key;
		}

		public byte[] getIv() {
			return iv;
		}
	}

	private XmppAxolotlMessage(final Element axolotlMessage, final Jid from) throws IllegalArgumentException {
		this.from = from;
		Element header = axolotlMessage.findChild(HEADER);
		this.sourceDeviceId = Integer.parseInt(header.getAttribute(SOURCEID));
		List<Element> keyElements = header.getChildren();
		this.keys = new HashMap<>(keyElements.size());
		for (Element keyElement : keyElements) {
			switch (keyElement.getName()) {
				case KEYTAG:
					try {
						Integer recipientId = Integer.parseInt(keyElement.getAttribute(REMOTEID));
						byte[] key = Base64.decode(keyElement.getContent(), Base64.DEFAULT);
						this.keys.put(recipientId, key);
					} catch (NumberFormatException e) {
						throw new IllegalArgumentException(e);
					}
					break;
				case IVTAG:
					if (this.iv != null) {
						throw new IllegalArgumentException("Duplicate iv entry");
					}
					iv = Base64.decode(keyElement.getContent(), Base64.DEFAULT);
					break;
				default:
					Log.w(Config.LOGTAG, "Unexpected element in header: " + keyElement.toString());
					break;
			}
		}
		Element payloadElement = axolotlMessage.findChild(PAYLOAD);
		if (payloadElement != null) {
			ciphertext = Base64.decode(payloadElement.getContent(), Base64.DEFAULT);
		}
	}

	public XmppAxolotlMessage(Jid from, int sourceDeviceId) {
		this.from = from;
		this.sourceDeviceId = sourceDeviceId;
		this.keys = new HashMap<>();
		this.iv = generateIv();
		this.innerKey = generateKey();
	}

	public static XmppAxolotlMessage fromElement(Element element, Jid from) {
		return new XmppAxolotlMessage(element, from);
	}

	private static byte[] generateKey() {
		try {
			KeyGenerator generator = KeyGenerator.getInstance(KEYTYPE);
			generator.init(128);
			return generator.generateKey().getEncoded();
		} catch (NoSuchAlgorithmException e) {
			Log.e(Config.LOGTAG, e.getMessage());
			return null;
		}
	}

	private static byte[] generateIv() {
		SecureRandom random = new SecureRandom();
		byte[] iv = new byte[16];
		random.nextBytes(iv);
		return iv;
	}

	public void encrypt(String plaintext) throws CryptoFailedException {
		try {
			SecretKey secretKey = new SecretKeySpec(innerKey, KEYTYPE);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
			this.ciphertext = cipher.doFinal(plaintext.getBytes());
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| IllegalBlockSizeException | BadPaddingException | NoSuchProviderException
				| InvalidAlgorithmParameterException e) {
			throw new CryptoFailedException(e);
		}
	}

	public Jid getFrom() {
		return this.from;
	}

	public int getSenderDeviceId() {
		return sourceDeviceId;
	}

	public byte[] getCiphertext() {
		return ciphertext;
	}

	public void addDevice(XmppAxolotlSession session) {
		byte[] key = session.processSending(innerKey);
		if (key != null) {
			keys.put(session.getRemoteAddress().getDeviceId(), key);
		}
	}

	public byte[] getInnerKey() {
		return innerKey;
	}

	public byte[] getIV() {
		return this.iv;
	}

	public Element toElement() {
		Element encryptionElement = new Element(CONTAINERTAG, AxolotlService.PEP_PREFIX);
		Element headerElement = encryptionElement.addChild(HEADER);
		headerElement.setAttribute(SOURCEID, sourceDeviceId);
		for (Map.Entry<Integer, byte[]> keyEntry : keys.entrySet()) {
			Element keyElement = new Element(KEYTAG);
			keyElement.setAttribute(REMOTEID, keyEntry.getKey());
			keyElement.setContent(Base64.encodeToString(keyEntry.getValue(), Base64.DEFAULT));
			headerElement.addChild(keyElement);
		}
		headerElement.addChild(IVTAG).setContent(Base64.encodeToString(iv, Base64.DEFAULT));
		if (ciphertext != null) {
			Element payload = encryptionElement.addChild(PAYLOAD);
			payload.setContent(Base64.encodeToString(ciphertext, Base64.DEFAULT));
		}
		return encryptionElement;
	}

	private byte[] unpackKey(XmppAxolotlSession session, Integer sourceDeviceId) {
		byte[] encryptedKey = keys.get(sourceDeviceId);
		return (encryptedKey != null) ? session.processReceiving(encryptedKey) : null;
	}

	public XmppAxolotlKeyTransportMessage getParameters(XmppAxolotlSession session, Integer sourceDeviceId) {
		byte[] key = unpackKey(session, sourceDeviceId);
		return (key != null)
				? new XmppAxolotlKeyTransportMessage(session.getFingerprint(), key, getIV())
				: null;
	}

	public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, Integer sourceDeviceId) throws CryptoFailedException {
		XmppAxolotlPlaintextMessage plaintextMessage = null;
		byte[] key = unpackKey(session, sourceDeviceId);
		if (key != null) {
			try {
				Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
				SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
				IvParameterSpec ivSpec = new IvParameterSpec(iv);

				cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

				String plaintext = new String(cipher.doFinal(ciphertext));
				plaintextMessage = new XmppAxolotlPlaintextMessage(plaintext, session.getFingerprint());

			} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
					| InvalidAlgorithmParameterException | IllegalBlockSizeException
					| BadPaddingException | NoSuchProviderException e) {
				throw new CryptoFailedException(e);
			}
		}
		return plaintextMessage;
	}
}
