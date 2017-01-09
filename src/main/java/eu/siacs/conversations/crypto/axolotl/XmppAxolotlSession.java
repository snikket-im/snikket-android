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

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;

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
	public byte[] processReceiving(AxolotlKey encryptedKey) {
		byte[] plaintext = null;
		FingerprintStatus status = getTrust();
		if (!status.isCompromised()) {
			try {
				try {
					PreKeyWhisperMessage message = new PreKeyWhisperMessage(encryptedKey.key);
					if (!message.getPreKeyId().isPresent()) {
						Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "PreKeyWhisperMessage did not contain a PreKeyId");
						return null;
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
					WhisperMessage message = new WhisperMessage(encryptedKey.key);
					plaintext = cipher.decrypt(message);
				} catch (InvalidKeyException | InvalidKeyIdException | UntrustedIdentityException e) {
					Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
				}
			} catch (LegacyMessageException | InvalidMessageException | DuplicateMessageException | NoSessionException e) {
				Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
			}

			if (plaintext != null) {
				if (!status.isActive()) {
					setTrust(status.toActive());
				}
			}
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
