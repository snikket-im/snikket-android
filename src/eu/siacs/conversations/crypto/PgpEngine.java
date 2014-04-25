package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;

import eu.siacs.conversations.entities.Account;

import android.app.PendingIntent;
import android.content.Intent;
import android.util.Log;

public class PgpEngine {
	private OpenPgpApi api;

	public PgpEngine(OpenPgpApi api) {
		this.api = api;
	}

	public String decrypt(Account account, String message) throws UserInputRequiredException,
			OpenPgpException {
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_DECRYPT_VERIFY);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(message.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			return os.toString();
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			throw new UserInputRequiredException((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
		case OpenPgpApi.RESULT_CODE_ERROR:
			throw new OpenPgpException(
					(OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
		default:
			return null;
		}
	}

	public String encrypt(Account account, long keyId, String message) throws UserInputRequiredException, OpenPgpException {
		Log.d("xmppService","called to pgpengine::encrypt");
		long[] keys = {keyId};
		Intent params = new Intent();
		params.setAction(OpenPgpApi.ACTION_ENCRYPT);
		params.putExtra(OpenPgpApi.EXTRA_KEY_IDS,keys);
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		
		InputStream is = new ByteArrayInputStream(message.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			StringBuilder encryptedMessageBody = new StringBuilder();
			String[] lines = os.toString().split("\n");
			for (int i = 3; i < lines.length - 1; ++i) {
				encryptedMessageBody.append(lines[i].trim());
			}
			Log.d("xmppService","encrpyted message: "+encryptedMessageBody.toString());
			return encryptedMessageBody.toString();
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			Log.d("xmppService","user input required");
			throw new UserInputRequiredException((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
		case OpenPgpApi.RESULT_CODE_ERROR:
			OpenPgpError error = (OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR);
			throw new OpenPgpException(error);
		default:
			return null;
		}
	}

	public long fetchKeyId(Account account, String status, String signature)
			throws OpenPgpException {
		if ((signature==null)||(api==null)) {
			return 0;
		}
		if (status==null) {
			status="";
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
		InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			OpenPgpSignatureResult sigResult
            = result.getParcelableExtra(OpenPgpApi.RESULT_SIGNATURE);
			if (sigResult==null) {
				return 0;
			} else {
				return sigResult.getKeyId();
			}
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			break;
		case OpenPgpApi.RESULT_CODE_ERROR:
			throw new OpenPgpException(
					(OpenPgpError) result.getParcelableExtra(OpenPgpApi.RESULT_ERROR));
		}
		return 0;
	}

	public String generateSignature(Account account, String status)
			throws UserInputRequiredException {
		Intent params = new Intent();
		params.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
		params.setAction(OpenPgpApi.ACTION_SIGN);
		params.putExtra(OpenPgpApi.EXTRA_ACCOUNT_NAME, account.getJid());
		InputStream is = new ByteArrayInputStream(status.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Intent result = api.executeApi(params, is, os);
		StringBuilder signatureBuilder = new StringBuilder();
		switch (result.getIntExtra(OpenPgpApi.RESULT_CODE, 0)) {
		case OpenPgpApi.RESULT_CODE_SUCCESS:
			String[] lines = os.toString().split("\n");
			for (int i = 7; i < lines.length - 1; ++i) {
				signatureBuilder.append(lines[i].trim());
			}
			break;
		case OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			throw new UserInputRequiredException((PendingIntent) result.getParcelableExtra(OpenPgpApi.RESULT_INTENT));
		case OpenPgpApi.RESULT_CODE_ERROR:
			break;
		}
		return signatureBuilder.toString();
	}

	public class UserInputRequiredException extends Exception {
		private static final long serialVersionUID = -6913480043269132016L;
		private PendingIntent pi;

		public UserInputRequiredException(PendingIntent pi) {
			this.pi = pi;
		}

		public PendingIntent getPendingIntent() {
			return this.pi;
		}
	}

	public class OpenPgpException extends Exception {
		private static final long serialVersionUID = -7324789703473056077L;
		private OpenPgpError error;

		public OpenPgpException(OpenPgpError openPgpError) {
			this.error = openPgpError;
		}

		public OpenPgpError getOpenPgpError() {
			return this.error;
		}
	}
}
