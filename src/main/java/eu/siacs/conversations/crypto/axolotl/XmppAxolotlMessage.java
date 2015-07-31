package eu.siacs.conversations.crypto.axolotl;

import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

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
	public static final String TAGNAME = "encrypted";
	public static final String HEADER = "header";
	public static final String SOURCEID = "sid";
	public static final String IVTAG = "iv";
	public static final String PAYLOAD = "payload";

	private static final String KEYTYPE = "AES";
	private static final String CIPHERMODE = "AES/GCM/NoPadding";
	private static final String PROVIDER = "BC";

	private byte[] innerKey;
	private byte[] ciphertext = null;
	private byte[] iv = null;
	private final Set<XmppAxolotlKeyElement> keyElements;
	private final Jid from;
	private final int sourceDeviceId;

	public static class XmppAxolotlKeyElement {
		public static final String TAGNAME = "key";
		public static final String REMOTEID = "rid";

		private final int recipientDeviceId;
		private final byte[] content;

		public XmppAxolotlKeyElement(int deviceId, byte[] content) {
			this.recipientDeviceId = deviceId;
			this.content = content;
		}

		public XmppAxolotlKeyElement(Element keyElement) {
			if (TAGNAME.equals(keyElement.getName())) {
				this.recipientDeviceId = Integer.parseInt(keyElement.getAttribute(REMOTEID));
				this.content = Base64.decode(keyElement.getContent(), Base64.DEFAULT);
			} else {
				throw new IllegalArgumentException("Argument not a <" + TAGNAME + "> Element!");
			}
		}

		public int getRecipientDeviceId() {
			return recipientDeviceId;
		}

		public byte[] getContents() {
			return content;
		}

		public Element toXml() {
			Element keyElement = new Element(TAGNAME);
			keyElement.setAttribute(REMOTEID, getRecipientDeviceId());
			keyElement.setContent(Base64.encodeToString(getContents(), Base64.DEFAULT));
			return keyElement;
		}
	}

	public static class XmppAxolotlPlaintextMessage {
		private final XmppAxolotlSession session;
		private final String plaintext;
		private final String fingerprint;

		public XmppAxolotlPlaintextMessage(XmppAxolotlSession session, String plaintext, String fingerprint) {
			this.session = session;
			this.plaintext = plaintext;
			this.fingerprint = fingerprint;
		}

		public String getPlaintext() {
			return plaintext;
		}

		public XmppAxolotlSession getSession() {
			return session;
		}

		public String getFingerprint() {
			return fingerprint;
		}
	}

	public XmppAxolotlMessage(Jid from, Element axolotlMessage) throws IllegalArgumentException {
		this.from = from;
		Element header = axolotlMessage.findChild(HEADER);
		this.sourceDeviceId = Integer.parseInt(header.getAttribute(SOURCEID));
		this.keyElements = new HashSet<>();
		for (Element keyElement : header.getChildren()) {
			switch (keyElement.getName()) {
				case XmppAxolotlKeyElement.TAGNAME:
					keyElements.add(new XmppAxolotlKeyElement(keyElement));
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
		this.keyElements = new HashSet<>();
		this.iv = generateIv();
		this.innerKey = generateKey();
	}

	public XmppAxolotlMessage(Jid from, int sourceDeviceId, String plaintext) throws CryptoFailedException {
		this(from, sourceDeviceId);
		this.encrypt(plaintext);
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

	private void encrypt(String plaintext) throws CryptoFailedException {
		try {
			SecretKey secretKey = new SecretKeySpec(innerKey, KEYTYPE);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);
			Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
			this.innerKey = secretKey.getEncoded();
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

	public Set<XmppAxolotlKeyElement> getKeyElements() {
		return keyElements;
	}

	public void addKeyElement(@Nullable XmppAxolotlKeyElement keyElement) {
		if (keyElement != null) {
			keyElements.add(keyElement);
		}
	}

	public byte[] getInnerKey() {
		return innerKey;
	}

	public byte[] getIV() {
		return this.iv;
	}

	public Element toXml() {
		Element encryptionElement = new Element(TAGNAME, AxolotlService.PEP_PREFIX);
		Element headerElement = encryptionElement.addChild(HEADER);
		headerElement.setAttribute(SOURCEID, sourceDeviceId);
		for (XmppAxolotlKeyElement header : keyElements) {
			headerElement.addChild(header.toXml());
		}
		headerElement.addChild(IVTAG).setContent(Base64.encodeToString(iv, Base64.DEFAULT));
		if (ciphertext != null) {
			Element payload = encryptionElement.addChild(PAYLOAD);
			payload.setContent(Base64.encodeToString(ciphertext, Base64.DEFAULT));
		}
		return encryptionElement;
	}


	public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, byte[] key, String fingerprint) throws CryptoFailedException {
		XmppAxolotlPlaintextMessage plaintextMessage = null;
		try {

			Cipher cipher = Cipher.getInstance(CIPHERMODE, PROVIDER);
			SecretKeySpec keySpec = new SecretKeySpec(key, KEYTYPE);
			IvParameterSpec ivSpec = new IvParameterSpec(iv);

			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

			String plaintext = new String(cipher.doFinal(ciphertext));
			plaintextMessage = new XmppAxolotlPlaintextMessage(session, plaintext, fingerprint);

		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
				| InvalidAlgorithmParameterException | IllegalBlockSizeException
				| BadPaddingException | NoSuchProviderException e) {
			throw new CryptoFailedException(e);
		}
		return plaintextMessage;
	}
}
