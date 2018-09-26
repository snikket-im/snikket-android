package eu.siacs.conversations.crypto.sasl;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Base64;
import android.util.LruCache;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.TagWriter;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
abstract class ScramMechanism extends SaslMechanism {
	// TODO: When channel binding (SCRAM-SHA1-PLUS) is supported in future, generalize this to indicate support and/or usage.
	private final static String GS2_HEADER = "n,,";
	private String clientFirstMessageBare;
	private final String clientNonce;
	private byte[] serverSignature = null;
	static HMac HMAC;
	static Digest DIGEST;
	private static final byte[] CLIENT_KEY_BYTES = "Client Key".getBytes();
	private static final byte[] SERVER_KEY_BYTES = "Server Key".getBytes();

	private static class KeyPair {
		final byte[] clientKey;
		final byte[] serverKey;

		KeyPair(final byte[] clientKey, final byte[] serverKey) {
			this.clientKey = clientKey;
			this.serverKey = serverKey;
		}
	}

	static {
		CACHE = new LruCache<String, KeyPair>(10) {
			protected KeyPair create(final String k) {
				// Map keys are "bytesToHex(JID),bytesToHex(password),bytesToHex(salt),iterations,SASL-Mechanism".
				// Changing any of these values forces a cache miss. `CryptoHelper.bytesToHex()'
				// is applied to prevent commas in the strings breaking things.
				final String[] kparts = k.split(",", 4);
				try {
					final byte[] saltedPassword, serverKey, clientKey;
					saltedPassword = hi(CryptoHelper.hexToString(kparts[1]).getBytes(),
							Base64.decode(CryptoHelper.hexToString(kparts[2]), Base64.DEFAULT), Integer.valueOf(kparts[3]));
					serverKey = hmac(saltedPassword, SERVER_KEY_BYTES);
					clientKey = hmac(saltedPassword, CLIENT_KEY_BYTES);

					return new KeyPair(clientKey, serverKey);
				} catch (final InvalidKeyException | NumberFormatException e) {
					return null;
				}
			}
		};
	}

	private static final LruCache<String, KeyPair> CACHE;

	protected State state = State.INITIAL;

	ScramMechanism(final TagWriter tagWriter, final Account account, final SecureRandom rng) {
		super(tagWriter, account, rng);

		// This nonce should be different for each authentication attempt.
		clientNonce = CryptoHelper.random(100,rng);
		clientFirstMessageBare = "";
	}

	@Override
	public String getClientFirstMessage() {
		if (clientFirstMessageBare.isEmpty() && state == State.INITIAL) {
			clientFirstMessageBare = "n=" + CryptoHelper.saslEscape(CryptoHelper.saslPrep(account.getUsername())) +
				",r=" + this.clientNonce;
			state = State.AUTH_TEXT_SENT;
		}
		return Base64.encodeToString(
				(GS2_HEADER + clientFirstMessageBare).getBytes(Charset.defaultCharset()),
				Base64.NO_WRAP);
	}

	@Override
	public String getResponse(final String challenge) throws AuthenticationException {
		switch (state) {
			case AUTH_TEXT_SENT:
				if (challenge == null) {
					throw new AuthenticationException("challenge can not be null");
				}
				byte[] serverFirstMessage;
				try {
					serverFirstMessage = Base64.decode(challenge, Base64.DEFAULT);
				} catch (IllegalArgumentException e) {
					throw new AuthenticationException("Unable to decode server challenge",e);
				}
				final Tokenizer tokenizer = new Tokenizer(serverFirstMessage);
				String nonce = "";
				int iterationCount = -1;
				String salt = "";
				for (final String token : tokenizer) {
					if (token.charAt(1) == '=') {
						switch (token.charAt(0)) {
							case 'i':
								try {
									iterationCount = Integer.parseInt(token.substring(2));
								} catch (final NumberFormatException e) {
									throw new AuthenticationException(e);
								}
								break;
							case 's':
								salt = token.substring(2);
								break;
							case 'r':
								nonce = token.substring(2);
								break;
							case 'm':
								/*
								 * RFC 5802:
								 * m: This attribute is reserved for future extensibility.  In this
								 * version of SCRAM, its presence in a client or a server message
								 * MUST cause authentication failure when the attribute is parsed by
								 * the other end.
								 */
								throw new AuthenticationException("Server sent reserved token: `m'");
						}
					}
				}

				if (iterationCount < 0) {
					throw new AuthenticationException("Server did not send iteration count");
				}
				if (nonce.isEmpty() || !nonce.startsWith(clientNonce)) {
					throw new AuthenticationException("Server nonce does not contain client nonce: " + nonce);
				}
				if (salt.isEmpty()) {
					throw new AuthenticationException("Server sent empty salt");
				}

				final String clientFinalMessageWithoutProof = "c=" + Base64.encodeToString(
						GS2_HEADER.getBytes(), Base64.NO_WRAP) + ",r=" + nonce;
				final byte[] authMessage = (clientFirstMessageBare + ',' + new String(serverFirstMessage) + ','
						+ clientFinalMessageWithoutProof).getBytes();

				// Map keys are "bytesToHex(JID),bytesToHex(password),bytesToHex(salt),iterations,SASL-Mechanism".
				final KeyPair keys = CACHE.get(
						CryptoHelper.bytesToHex(account.getJid().asBareJid().toString().getBytes()) + ","
						+ CryptoHelper.bytesToHex(account.getPassword().getBytes()) + ","
						+ CryptoHelper.bytesToHex(salt.getBytes()) + ","
						+ String.valueOf(iterationCount)
						+ getMechanism()
						);
				if (keys == null) {
					throw new AuthenticationException("Invalid keys generated");
				}
				final byte[] clientSignature;
				try {
					serverSignature = hmac(keys.serverKey, authMessage);
					final byte[] storedKey = digest(keys.clientKey);

					clientSignature = hmac(storedKey, authMessage);

				} catch (final InvalidKeyException e) {
					throw new AuthenticationException(e);
				}

				final byte[] clientProof = new byte[keys.clientKey.length];

				for (int i = 0; i < clientProof.length; i++) {
					clientProof[i] = (byte) (keys.clientKey[i] ^ clientSignature[i]);
				}


				final String clientFinalMessage = clientFinalMessageWithoutProof + ",p=" +
					Base64.encodeToString(clientProof, Base64.NO_WRAP);
				state = State.RESPONSE_SENT;
				return Base64.encodeToString(clientFinalMessage.getBytes(), Base64.NO_WRAP);
			case RESPONSE_SENT:
				try {
					final String clientCalculatedServerFinalMessage = "v=" +
						Base64.encodeToString(serverSignature, Base64.NO_WRAP);
					if (!clientCalculatedServerFinalMessage.equals(new String(Base64.decode(challenge, Base64.DEFAULT)))) {
						throw new Exception();
					}
					state = State.VALID_SERVER_RESPONSE;
					return "";
				} catch(Exception e) {
					throw new AuthenticationException("Server final message does not match calculated final message");
				}
			default:
				throw new InvalidStateException(state);
		}
	}

	private static synchronized byte[] hmac(final byte[] key, final byte[] input)
		throws InvalidKeyException {
		HMAC.init(new KeyParameter(key));
		HMAC.update(input, 0, input.length);
		final byte[] out = new byte[HMAC.getMacSize()];
		HMAC.doFinal(out, 0);
		return out;
	}

	public static synchronized byte[] digest(byte[] bytes) {
		DIGEST.reset();
		DIGEST.update(bytes, 0, bytes.length);
		final byte[] out = new byte[DIGEST.getDigestSize()];
		DIGEST.doFinal(out, 0);
		return out;
	}

	/*
	 * Hi() is, essentially, PBKDF2 [RFC2898] with HMAC() as the
	 * pseudorandom function (PRF) and with dkLen == output length of
	 * HMAC() == output length of H().
	 */
	private static synchronized byte[] hi(final byte[] key, final byte[] salt, final int iterations)
		throws InvalidKeyException {
		byte[] u = hmac(key, CryptoHelper.concatenateByteArrays(salt, CryptoHelper.ONE));
		byte[] out = u.clone();
		for (int i = 1; i < iterations; i++) {
			u = hmac(key, u);
			for (int j = 0; j < u.length; j++) {
				out[j] ^= u[j];
			}
		}
		return out;
	}
}
