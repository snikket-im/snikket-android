package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpApi.IOpenPgpCallback;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

public class PgpEngine {
	private OpenPgpApi api;

	public PgpEngine(OpenPgpApi api) {
		this.api = api;
	}

	public void decrypt(final Message message, final OnPgpEngineResult callback) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, message
				.getConversation().getAccount().getJid());
		InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
		final OutputStream os = new ByteArrayOutputStream();
		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {

			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
						OpenPgpApi.RESULT_CODE_ERROR)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					message.setBody(os.toString());
					message.setEncryption(Message.ENCRYPTION_DECRYPTED);
					callback.success();
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error((OpenPgpError) result
							.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
					return;
				default:
					return;
				}
			}
		});
	}

	public void encrypt(Account account, long keyId, Message message,
			final OnPgpEngineResult callback) {
		Log.d("xmppService", "called to pgpengine::encrypt");
		long[] keys = { keyId };
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_ENCRYPT);
		params.putExtra(OpenPgpApi.EXTRA_KEY_IDS, keys);
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());

		InputStream is = new ByteArrayInputStream(message.getBody().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE,
				OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			StringBuilder encryptedMessageBody = new StringBuilder();
			String[] lines = os.toString().split("\n");
			for (int i = 3; i < lines.length - 1; ++i) {
				encryptedMessageBody.append(lines[i].trim());
			}
			message.setEncryptedBody(encryptedMessageBody.toString());
			callback.success();
			return;
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			callback.userInputRequried((PendingIntent) result
					.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
			return;
		case OpenPgpApi.RESULT_CODE_ERROR:
			callback.error((OpenPgpError) result
					.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
			return;
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
			Log.d("xmppService","user interaction required");
			return 0;
		case OpenPgpApi.RESULT_CODE_ERROR:
			Log.d("xmppService","pgp error");
			return 0;
		}
		return 0;
	}

	public void generateSignature(final Account account, String status,
			final OnPgpEngineResult callback) {
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
					String[] lines = os.toString().split("\n");
					for (int i = 7; i < lines.length - 1; ++i) {
						signatureBuilder.append(lines[i].trim());
					}
					account.setKey("pgp_signature", signatureBuilder.toString());
					callback.success();
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error((OpenPgpError) result
							.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
					return;
				}
			}
		});
	}
	
	public void hasKey(Account account, long keyId, final OnPgpEngineResult callback) {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_GET_KEY);
		params.putExtra(OpenPgpApi.EXTRA_KEY_ID, keyId);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(new byte[0]);
		OutputStream os = new ByteArrayOutputStream();
		api.executeApiAsync(params, is, os, new IOpenPgpCallback() {
			
			@Override
			public void onReturn(Intent result) {
				switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
				case OpenPgpApi.RESULT_CODE_SUCCESS:
					callback.success();
					return;
				case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
					callback.userInputRequried((PendingIntent) result
							.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
					return;
				case OpenPgpApi.RESULT_CODE_ERROR:
					callback.error((OpenPgpError) result
							.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
					return;
				}
			}
		});
	}
}
