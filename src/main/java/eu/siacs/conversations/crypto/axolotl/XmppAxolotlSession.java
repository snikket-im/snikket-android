package eu.siacs.conversations.crypto.axolotl;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.DuplicateMessageException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.InvalidVersionException;
import org.whispersystems.libsignal.LegacyMessageException;
import org.whispersystems.libsignal.NoSessionException;
import org.whispersystems.libsignal.SessionCipher;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.protocol.PreKeySignalMessage;
import org.whispersystems.libsignal.protocol.SignalMessage;
import org.whispersystems.libsignal.util.guava.Optional;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;

public class XmppAxolotlSession implements Comparable<XmppAxolotlSession> {
	private final SessionCipher cipher;
	private final SQLiteAxolotlStore sqLiteAxolotlStore;
	private final SignalProtocolAddress remoteAddress;
	private final Account account;
	private IdentityKey identityKey;
	private Integer preKeyId = null;
	private boolean fresh = true;

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, SignalProtocolAddress remoteAddress, IdentityKey identityKey) {
		this(account, store, remoteAddress);
		this.identityKey = identityKey;
	}

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, SignalProtocolAddress remoteAddress) {
		this.cipher = new SessionCipher(store, remoteAddress);
		this.remoteAddress = remoteAddress;
		this.sqLiteAxolotlStore = store;
		this.account = account;
	}

	public Integer getPreKeyIdAndReset() {
		final Integer preKeyId = this.preKeyId;
		this.preKeyId = null;
		return preKeyId;
	}

	public String getFingerprint() {
		return identityKey == null ? null : CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize());
	}

	public IdentityKey getIdentityKey() {
		return identityKey;
	}

	public SignalProtocolAddress getRemoteAddress() {
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
				if (encryptedKey.prekey) {
					PreKeySignalMessage preKeySignalMessage = new PreKeySignalMessage(encryptedKey.key);
					Optional<Integer> optionalPreKeyId = preKeySignalMessage.getPreKeyId();
					IdentityKey identityKey = preKeySignalMessage.getIdentityKey();
					if (!optionalPreKeyId.isPresent()) {
						throw new CryptoFailedException("PreKeyWhisperMessage did not contain a PreKeyId");
					}
					preKeyId = optionalPreKeyId.get();
					if (this.identityKey != null && !this.identityKey.equals(identityKey)) {
						throw new CryptoFailedException("Received PreKeyWhisperMessage but preexisting identity key changed.");
					}
					this.identityKey = identityKey;
					plaintext = cipher.decrypt(preKeySignalMessage);
				} else {
					SignalMessage signalMessage = new SignalMessage(encryptedKey.key);
					plaintext = cipher.decrypt(signalMessage);
					preKeyId = null; //better safe than sorry because we use that to do special after prekey handling
				}
			} catch (InvalidVersionException | InvalidKeyException | LegacyMessageException | InvalidMessageException | DuplicateMessageException | NoSessionException | InvalidKeyIdException | UntrustedIdentityException e) {
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
	public AxolotlKey processSending(@NonNull byte[] outgoingMessage, boolean ignoreSessionTrust) {
		FingerprintStatus status = getTrust();
		if (ignoreSessionTrust || status.isTrustedAndActive()) {
			try {
				CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
				return new AxolotlKey(ciphertextMessage.serialize(),ciphertextMessage.getType() == CiphertextMessage.PREKEY_TYPE);
			} catch (UntrustedIdentityException e) {
				return null;
			}
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
