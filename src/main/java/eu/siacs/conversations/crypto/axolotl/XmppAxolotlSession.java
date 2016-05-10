package eu.siacs.conversations.crypto.axolotl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.whispersystems.libaxolotl.AxolotlAddress;
import org.whispersystems.libaxolotl.DuplicateMessageException;
import org.whispersystems.libaxolotl.IdentityKey;
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
	private IdentityKey identityKey;
	private Integer preKeyId = null;
	private boolean fresh = true;

	public enum Trust {
		UNDECIDED(0),
		TRUSTED(1),
		UNTRUSTED(2),
		COMPROMISED(3),
		INACTIVE_TRUSTED(4),
		INACTIVE_UNDECIDED(5),
		INACTIVE_UNTRUSTED(6),
		TRUSTED_X509(7),
		INACTIVE_TRUSTED_X509(8);

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
				case TRUSTED_X509:
					return "Trusted (X509) " + getCode();
				case INACTIVE_TRUSTED_X509:
					return "Inactive (Trusted (X509)) " + getCode();
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

		public boolean trusted() {
			return this == TRUSTED_X509 || this == TRUSTED;
		}

		public boolean trustedInactive() {
			return this == INACTIVE_TRUSTED_X509 || this == INACTIVE_TRUSTED;
		}
	}

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, AxolotlAddress remoteAddress, IdentityKey identityKey) {
		this(account, store, remoteAddress);
		this.identityKey = identityKey;
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
		return identityKey == null ? null : identityKey.getFingerprint().replaceAll("\\s", "");
	}

	public IdentityKey getIdentityKey() {
		return identityKey;
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
		sqLiteAxolotlStore.setFingerprintTrust(getFingerprint(), trust);
	}

	protected Trust getTrust() {
		Trust trust = sqLiteAxolotlStore.getFingerprintTrust(getFingerprint());
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
			case INACTIVE_TRUSTED_X509:
			case TRUSTED_X509:
				try {
					try {
						PreKeyWhisperMessage message = new PreKeyWhisperMessage(encryptedKey);
						if (!message.getPreKeyId().isPresent()) {
							Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "PreKeyWhisperMessage did not contain a PreKeyId");
							break;
						}
						Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "PreKeyWhisperMessage received, new session ID:" + message.getSignedPreKeyId() + "/" + message.getPreKeyId());
						IdentityKey msgIdentityKey = message.getIdentityKey();
						if (this.identityKey != null && !this.identityKey.equals(msgIdentityKey)) {
							Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Had session with fingerprint " + this.getFingerprint() + ", received message with fingerprint " + msgIdentityKey.getFingerprint());
						} else {
							this.identityKey = msgIdentityKey;
							plaintext = cipher.decrypt(message);
							preKeyId = message.getPreKeyId().get();
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

				if (plaintext != null) {
					if (trust == Trust.INACTIVE_TRUSTED) {
						setTrust(Trust.TRUSTED);
					} else if (trust == Trust.INACTIVE_TRUSTED_X509) {
						setTrust(Trust.TRUSTED_X509);
					}
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
		if (trust.trusted()) {
			CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
			return ciphertextMessage.serialize();
		} else {
			return null;
		}
	}
}
