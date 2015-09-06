package eu.siacs.conversations.crypto.axolotl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.InvalidKeyException;
import org.whispersystems.libaxolotl.InvalidKeyIdException;
import org.whispersystems.libaxolotl.InvalidMessageException;
import org.whispersystems.libaxolotl.InvalidVersionException;
import org.whispersystems.libaxolotl.LegacyMessageException;
import org.whispersystems.libaxolotl.NoSessionException;
import org.whispersystems.libaxolotl.SessionCipher;
import org.whispersystems.libaxolotl.UntrustedIdentityException;
import org.whispersystems.libaxolotl.protocol.CiphertextMessage;
import org.whispersystems.libaxolotl.protocol.PreKeyWhisperMessage;
import org.whispersystems.libaxolotl.protocol.WhisperMessage;

import java.util.HashMap;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;

public class XmppAxolotlSession {
	private final SessionCipher cipher;
	private final SQLiteAxolotlStore sqLiteAxolotlStore;
	private final AxolotlAddress remoteAddress;
	private final Account account;
	private String fingerprint = null;
	private Integer preKeyId = null;
	private boolean fresh = true;

	public enum Trust {
		UNDECIDED(0),
		TRUSTED(1),
		UNTRUSTED(2),
		COMPROMISED(3),
		INACTIVE_TRUSTED(4),
		INACTIVE_UNDECIDED(5),
		INACTIVE_UNTRUSTED(6);

		private static final Map<Integer, Trust> trustsByValue = new HashMap<>();

		static {
			for (Trust trust : Trust.values()) {
				trustsByValue.put(trust.getCode(), trust);
			}
		}

		private final int code;

		Trust(int code) {
			this.code = code;
		}

		public int getCode() {
			return this.code;
		}

		public String toString() {
			switch (this) {
				case UNDECIDED:
					return "Trust undecided " + getCode();
				case TRUSTED:
					return "Trusted " + getCode();
				case COMPROMISED:
					return "Compromised " + getCode();
				case INACTIVE_TRUSTED:
					return "Inactive (Trusted)" + getCode();
				case INACTIVE_UNDECIDED:
					return "Inactive (Undecided)" + getCode();
				case INACTIVE_UNTRUSTED:
					return "Inactive (Untrusted)" + getCode();
				case UNTRUSTED:
				default:
					return "Untrusted " + getCode();
			}
		}

		public static Trust fromBoolean(Boolean trusted) {
			return trusted ? TRUSTED : UNTRUSTED;
		}

		public static Trust fromCode(int code) {
			return trustsByValue.get(code);
		}
	}

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, AxolotlAddress remoteAddress, String fingerprint) {
		this(account, store, remoteAddress);
		this.fingerprint = fingerprint.replaceAll("\\s","");
	}

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, AxolotlAddress remoteAddress) {
		this.cipher = new SessionCipher(store, remoteAddress);
		this.remoteAddress = remoteAddress;
		this.sqLiteAxolotlStore = store;
		this.account = account;
	}

	public Integer getPreKeyId() {
		return preKeyId;
	}

	public void resetPreKeyId() {

		preKeyId = null;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public AxolotlAddress getRemoteAddress() {
		return remoteAddress;
	}

	public boolean isFresh() {
		return fresh;
	}

	public void setNotFresh() {
		this.fresh = false;
	}

	protected void setTrust(Trust trust) {
		sqLiteAxolotlStore.setFingerprintTrust(fingerprint, trust);
	}

	protected Trust getTrust() {
		Trust trust = sqLiteAxolotlStore.getFingerprintTrust(fingerprint);
		return (trust == null) ? Trust.UNDECIDED : trust;
	}

	@Nullable
	public byte[] processReceiving(byte[] encryptedKey) {
		byte[] plaintext = null;
		Trust trust = getTrust();
		switch (trust) {
			case INACTIVE_TRUSTED:
			case UNDECIDED:
			case UNTRUSTED:
			case TRUSTED:
				try {
					try {
						PreKeyWhisperMessage message = new PreKeyWhisperMessage(encryptedKey);
						Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "PreKeyWhisperMessage received, new session ID:" + message.getSignedPreKeyId() + "/" + message.getPreKeyId());
						String fingerprint = message.getIdentityKey().getFingerprint().replaceAll("\\s", "");
						if (this.fingerprint != null && !this.fingerprint.equals(fingerprint)) {
							Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Had session with fingerprint " + this.fingerprint + ", received message with fingerprint " + fingerprint);
						} else {
							this.fingerprint = fingerprint;
							plaintext = cipher.decrypt(message);
							if (message.getPreKeyId().isPresent()) {
								preKeyId = message.getPreKeyId().get();
							}
						}
					} catch (InvalidMessageException | InvalidVersionException e) {
						Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "WhisperMessage received");
						WhisperMessage message = new WhisperMessage(encryptedKey);
						plaintext = cipher.decrypt(message);
					} catch (InvalidKeyException | InvalidKeyIdException | UntrustedIdentityException e) {
						Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
					}
				} catch (LegacyMessageException | InvalidMessageException | DuplicateMessageException | NoSessionException e) {
					Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
				}

				if (plaintext != null && trust == Trust.INACTIVE_TRUSTED) {
					setTrust(Trust.TRUSTED);
				}

				break;

			case COMPROMISED:
			default:
				// ignore
				break;
		}
		return plaintext;
	}

	@Nullable
	public byte[] processSending(@NonNull byte[] outgoingMessage) {
		Trust trust = getTrust();
		if (trust == Trust.TRUSTED) {
			CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
			return ciphertextMessage.serialize();
		} else {
			return null;
		}
	}
}
