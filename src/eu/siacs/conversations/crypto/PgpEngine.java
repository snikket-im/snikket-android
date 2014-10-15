package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.UiCallback;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Log;

public class PgpEngine {
	private OpenPgpApi api;
	private XmppConnectionService mXmppConnectionService;

	public PgpEngine(OpenPgpApi api, XmppConnectionService service) {
		this.api = api;
		this.mXmppConnectionService = service;
	}

	public void decrypt(final Message message,
			final UiCallback<Message> callback) {
		Log.d(Config.LOGTAG, "decrypting message " + message.getUuid());
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message
				.getConversation().getAccount().getJid());
		if (message.getType() == Message.TYPE_TEXT) {
			InputStream is = new ByteArrayInputStream(message.getBody()
					.getBytes());
			final OutputStream os = new ByteArrayOutputStream();
			api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

				@Override
				public void onReturn(Intent result) {
					switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
							OpenPgpApi.RESULT_CODE_ERROR)) {
					case OpenPgpApi.RESULT_CODE_SUCCESS:
						try {
							os.flush();
							if (message.getEncryption() == Message.ENCRYPTION_PGP) {
								message.setBody(os.toString());
								message.setEncryption(Message.ENCRYPTION_DECRYPTED);
								callback.success(message);
							}
						} catch (IOException e) {
							callback.error(R.string.openpgp_error, message);
							return;
						}

						return;
					case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
						callback.userInputRequried((PendingIntent) result
								.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
								message);
						return;
					case OpenPgpApi.RESULT_CODE_ERROR:
						OpenPgpError error = result
								.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
						Log.d(Config.LOGTAG,
								"openpgp error: " + error.getMessage());
						callback.error(R.string.openpgp_error, message);
						return;
					default:
						return;
					}
				}
			});
		} else if (message.getType() == Message.TYPE_IMAGE) {
			try {
				final DownloadableFile inputFile = this.mXmppConnectionService
						.getFileBackend().getFile(message, false);
				final DownloadableFile outputFile = this.mXmppConnectionService
						.getFileBackend().getFile(message, true);
				outputFile.createNewFile();
				InputStream is = new FileInputStream(inputFile);
				OutputStream os = new FileOutputStream(outputFile);
				api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

					@Override
					public void onReturn(Intent result) {
						switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
								OpenPgpApi.RESULT_CODE_ERROR)) {
						case OpenPgpApi.RESULT_CODE_SUCCESS:
							BitmapFactory.Options options = new BitmapFactory.Options();
							options.inJustDecodeBounds = true;
							BitmapFactory.decodeFile(
									outputFile.getAbsolutePath(), options);
							int imageHeight = options.outHeight;
							int imageWidth = options.outWidth;
							message.setBody(Long.toString(outputFile.getSize())
									+ ',' + imageWidth + ',' + imageHeight);
							message.setEncryption(Message.ENCRYPTION_DECRYPTED);
							PgpEngine.this.mXmppConnectionService
									.updateMessage(message);
							;
							callback.success(message);
							return;
						case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
							callback.userInputRequried(
									(PendingIntent) result
											.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
									message);
							return;
						case OpenPgpApi.RESULT_CODE_ERROR:
							callback.error(R.string.openpgp_error, message);
							return;
						default:
							return;
						}
					}
				});
			} catch (FileNotFoundException e) {
				callback.error(R.string.error_decrypting_file, message);
			} catch (IOException e) {
				callback.error(R.string.error_decrypting_file, message);
			}

		}
	}

	public void encrypt(final Message message,
			final UiCallback<Message> callback) {

		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_ENCRYPT);
		if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
			long[] keys = { message.getConversation().getContact()
					.getPgpKeyId() };
			params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keys);
		} else {
			params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, message.getConversation()
					.getMucOptions().getPgpKeyIds());
		}
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message
				.getConversation().getAccount().getJid());

		if (message.getType() == Message.TYPE_TEXT) {
			params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);

			InputStream is = new ByteArrayInputStream(message.getBody()
					.getBytes());
			final OutputStream os = new ByteArrayOutputStream();
			api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

				@Override
				public void onReturn(Intent result) {
					switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
							OpenPgpApi.RESULT_CODE_ERROR)) {
					case OpenPgpApi.RESULT_CODE_SUCCESS:
						try {
							os.flush();
							StringBuilder encryptedMessageBody = new StringBuilder();
							String[] lines = os.toString().split("\n");
							for (int i = 2; i < lines.length - 1; ++i) {
								if (!lines[i].contains("Version")) {
									encryptedMessageBody.append(lines[i].trim());
								}
							}
							message.setEncryptedBody(encryptedMessageBody
									.toString());
							callback.success(message);
						} catch (IOException e) {
							callback.error(R.string.openpgp_error, message);
						}

						break;
					case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
						callback.userInputRequried((PendingIntent) result
								.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
								message);
						break;
					case OpenPgpApi.RESULT_CODE_ERROR:
						callback.error(R.string.openpgp_error, message);
						break;
					}
				}
			});
		} else if (message.getType() == Message.TYPE_IMAGE) {
			try {
				DownloadableFile inputFile = this.mXmppConnectionService
						.getFileBackend().getFile(message, true);
				DownloadableFile outputFile = this.mXmppConnectionService
						.getFileBackend().getFile(message, false);
				outputFile.createNewFile();
				InputStream is = new FileInputStream(inputFile);
				OutputStream os = new FileOutputStream(outputFile);
				api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

					@Override
					public void onReturn(Intent result) {
						switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
								OpenPgpApi.RESULT_CODE_ERROR)) {
						case OpenPgpApi.RESULT_CODE_SUCCESS:
							callback.success(message);
							break;
						case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
							callback.userInputRequried(
									(PendingIntent) result
											.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
									message);
							break;
						case OpenPgpApi.RESULT_CODE_ERROR:
							callback.error(R.string.openpgp_error, message);
							break;
						}
					}
				});
			} catch (FileNotFoundException e) {
				Log.d(Config.LOGTAG, "file not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "io exception during file encrypt");
			}
		}
	}

	public long fetchKeyId(Account account, String status, String signature) {
		if ((signature == null) || (api == null)) {
			return 0;
		}
		if (status == null) {
			status = "";
		}
		StringBuilder pgpSig = new StringBuilder();
		pgpSig.append("-----BEGIN PGP SIGNED MESSAGE-----");
		pgpSig.append('\n');
		pgpSig.append('\n');
		pgpSig.append(status);
		pgpSig.append('\n');
		pgpSig.append("-----BEGIN PGP SIGNATURE-----");
		pgpSig.append('\n');
		pgpSig.append('\n');
		pgpSig.append(signature.replace("\n", "").trim());
		pgpSig.append('\n');
		pgpSig.append("-----END PGP SIGNATURE-----");
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
				OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			OpenPgpSignatureResult sigResult = result
					.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
			if (sigResult != null) {
				return sigResult.getKeyId();
			} else {
				return 0;
			}
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			return 0;
		case OpenPgpApi.RESULT_CODE_ERROR:
			Log.d(Config.LOGTAG,
					"openpgp error: "
							+ ((OpenPgpError) result
									.getParcelableExtra(OpenPgpApi.RESULT_ERROR))
									.getMessage());
			return 0;
		}
		return 0;
	}

	public void generateSignature(final Account account, String status,
			final UiCallback<Account> callback) {
		Intent params = new Intent();
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.setAction(OpenPgpApi.ACTION_SIGN);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(status.getBytes());
		final OutputStream os = new ByteArrayOutputStream();
		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					StringBuilder signatureBuilder = new StringBuilder();
					try {
						os.flush();
						String[] lines = os.toString().split("\n");
						boolean sig = false;
						for (String line : lines) {
							if (sig) {
								if (line.contains("END PGP SIGNATURE")) {
									sig = false;
								} else {
									if (!line.contains("Version")) {
										signatureBuilder.append(line.trim());
									}
								}
							}
							if (line.contains("BEGIN PGP SIGNATURE")) {
								sig = true;
							}
						}
					} catch (IOException e) {
						callback.error(R.string.openpgp_error, account);
						return;
					}
					account.setKey("pgp_signature", signatureBuilder.toString());
					callback.success(account);
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
							account);
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error(R.string.openpgp_error, account);
					return;
				}
			}
		});
	}

	public void hasKey(final Contact contact, final UiCallback<Contact> callback) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
				.getJid());
		api.executeApiAsync(params, null, null, new IOpenPgpCallback() {

			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					callback.success(contact);
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT),
							contact);
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error(R.string.openpgp_error, contact);
					return;
				}
			}
		});
	}

	public PendingIntent getIntentForKey(Contact contact) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, contact.getPgpKeyId());
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, contact.getAccount()
				.getJid());
		Intent result = api.executeApi(params, null, null);
		return (PendingIntent) result
				.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
	}

	public PendingIntent getIntentForKey(Account account, long pgpKeyId) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, pgpKeyId);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		Intent result = api.executeApi(params, null, null);
		return (PendingIntent) result
				.getParcelableExtra(OpenPgpApi.RESULT_INTENT);
	}
}
