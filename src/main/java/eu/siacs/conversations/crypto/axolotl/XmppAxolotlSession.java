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

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;

public class XmppAxolotlSession {
	private final SessionCipher cipher;
	private Integer preKeyId = null;
	private final SQLiteAxolotlStore sqLiteAxolotlStore;

	public AxolotlAddress getRemoteAddress() {
		return remoteAddress;
	}

	private final AxolotlAddress remoteAddress;
	private final Account account;
	private String fingerprint = null;

	public XmppAxolotlSession(Account account, SQLiteAxolotlStore store, AxolotlAddress remoteAddress, String fingerprint) {
		this(account, store, remoteAddress);
		this.fingerprint = fingerprint;
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

	protected void setTrust(SQLiteAxolotlStore.Trust trust) {
		sqLiteAxolotlStore.setFingerprintTrust(fingerprint, trust);
	}

	protected SQLiteAxolotlStore.Trust getTrust() {
		return sqLiteAxolotlStore.getFingerprintTrust(fingerprint);
	}

	@Nullable
	public byte[] processReceiving(XmppAxolotlMessage.XmppAxolotlMessageHeader incomingHeader) {
		byte[] plaintext = null;
		SQLiteAxolotlStore.Trust trust = getTrust();
		switch (trust) {
			case INACTIVE:
			case UNDECIDED:
			case UNTRUSTED:
			case TRUSTED:
				try {
					try {
						PreKeyWhisperMessage message = new PreKeyWhisperMessage(incomingHeader.getContents());
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
						WhisperMessage message = new WhisperMessage(incomingHeader.getContents());
						plaintext = cipher.decrypt(message);
					} catch (InvalidKeyException | InvalidKeyIdException | UntrustedIdentityException e) {
						Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
					}
				} catch (LegacyMessageException | InvalidMessageException | DuplicateMessageException | NoSessionException e) {
					Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error decrypting axolotl header, " + e.getClass().getName() + ": " + e.getMessage());
				}

				if (plaintext != null && trust == SQLiteAxolotlStore.Trust.INACTIVE) {
					setTrust(SQLiteAxolotlStore.Trust.TRUSTED);
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
	public XmppAxolotlMessage.XmppAxolotlMessageHeader processSending(@NonNull byte[] outgoingMessage) {
		SQLiteAxolotlStore.Trust trust = getTrust();
		if (trust == SQLiteAxolotlStore.Trust.TRUSTED) {
			CiphertextMessage ciphertextMessage = cipher.encrypt(outgoingMessage);
			XmppAxolotlMessage.XmppAxolotlMessageHeader header =
					new XmppAxolotlMessage.XmppAxolotlMessageHeader(remoteAddress.getDeviceId(),
							ciphertextMessage.serialize());
			return header;
		} else {
			return null;
		}
	}
}
