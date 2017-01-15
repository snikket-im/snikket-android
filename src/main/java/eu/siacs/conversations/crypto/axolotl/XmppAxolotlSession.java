package eu.siacs.conversations.crypto.axolotl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.bouncycastle.math.ec.PreCompInfo;
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
import org.whispersystems.libaxolotl.util.guava.Optional;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;

public class XmppAxolotlSession implements Comparable<XmppAxolotlSession> {
	private final SessionCipher cipher;
	private final SQLiteAxolotlStore sqLiteAxolotlStore;
	private final AxolotlAddress remoteAddress;
	private final Account account;
	private IdentityKey identityKey;
	private Integer preKeyId = null;
	private boolean fresh = true;

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

	protected void setTrust(FingerprintStatus status) {
		sqLiteAxolotlStore.setFingerprintStatus(getFingerprint(), status);
	}

	public FingerprintStatus getTrust() {
		FingerprintStatus status = sqLiteAxolotlStore.getFingerprintStatus(getFingerprint());
		return (status == null) ? FingerprintStatus.createActiveUndecided() : status;
	}

	@Nullable
	public byte[] processReceiving(AxolotlKey encryptedKey) throws CryptoFailedException {
		byte[] plaintext;
		FingerprintStatus status = getTrust();
		if (!status.isCompromised()) {
			try {
				CiphertextMessage ciphertextMessage;
				try {
					ciphertextMessage = new PreKeyWhisperMessage(encryptedKey.key);
					Optional<Integer> optionalPreKeyId = ((PreKeyWhisperMessage) ciphertextMessage).getPreKeyId();
					IdentityKey identityKey = ((PreKeyWhisperMessage) ciphertextMessage).getIdentityKey();
					if (!optionalPreKeyId.isPresent()) {
						throw new CryptoFailedException("PreKeyWhisperMessage did not contain a PreKeyId");
					}
					preKeyId = optionalPreKeyId.get();
					if (this.identityKey != null && !this.identityKey.equals(identityKey)) {
						throw new CryptoFailedException("Received PreKeyWhisperMessage but preexisting identity key changed.");
					}
					this.identityKey = identityKey;
				} catch (InvalidVersionException | InvalidMessageException e) {
					ciphertextMessage = new WhisperMessage(encryptedKey.key);
				}
				if (ciphertextMessage instanceof PreKeyWhisperMessage) {
					plaintext = cipher.decrypt((PreKeyWhisperMessage) ciphertextMessage);
				} else {
					plaintext = cipher.decrypt((WhisperMessage) ciphertextMessage);
				}
			} catch (InvalidKeyException | LegacyMessageException | InvalidMessageException | DuplicateMessageException | NoSessionException | InvalidKeyIdException | UntrustedIdentityException e) {
				if (!(e instanceof DuplicateMessageException)) {
					e.printStackTrace();
				}
				throw new CryptoFailedException("Error decrypting WhisperMessage " + e.getClass().getSimpleName() + ": " + e.getMessage());
			}
			if (!status.isActive()) {
				setTrust(status.toActive());
			}
		} else {
			throw new CryptoFailedException("not encrypting omemo message from fingerprint "+getFingerprint()+" because it was marked as compromised");
		}
		return plaintext;
	}

	@Nullable
	public AxolotlKey processSending(@NonNull byte[] outgoingMessage) {
		FingerprintStatus status = getTrust();
		if (status.isTrustedAndActive()) {
			CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
			return new AxolotlKey(ciphertextMessage.serialize(),ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE);
		} else {
			return null;
		}
	}

	public Account getAccount() {
		return account;
	}

	@Override
	public int compareTo(XmppAxolotlSession o) {
		return getTrust().compareTo(o.getTrust());
	}

	public static class AxolotlKey {


		public final byte[] key;
		public final boolean prekey;

		public AxolotlKey(byte[] key, boolean prekey) {
			this.key = key;
			this.prekey = prekey;
		}
	}
}
