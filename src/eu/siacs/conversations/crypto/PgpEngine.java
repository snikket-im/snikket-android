package eu.siacs.conversations.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpConstants;

import android.app.PendingIntent;
import android.os.Bundle;
import android.util.Log;

public class PgpEngine {
	private OpenPgpApi api;

	public PgpEngine(OpenPgpApi api) {
		this.api = api;
	}

	public String decrypt(String message) throws UserInputRequiredException,
			OpenPgpException {
		InputStream is = new ByteArrayInputStream(message.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Bundle result = api.decryptAndVerify(is, os);
		switch (result.getInt(OpenPgpConstants.RESULT_CODE)) {
		case OpenPgpConstants.RESULT_CODE_SUCCESS:
			return os.toString();
		case OpenPgpConstants.RESULT_CODE_USER_INTERACTION_REQUIRED:
			throw new UserInputRequiredException(
					(PendingIntent) result
							.getParcelable(OpenPgpConstants.RESULT_INTENT));
		case OpenPgpConstants.RESULT_CODE_ERROR:
			throw new OpenPgpException(
					(OpenPgpError) result
							.getParcelable(OpenPgpConstants.RESULT_ERRORS));
		default:
			return null;
		}
	}

	public String encrypt(long keyId, String message) {
		Bundle params = new Bundle();
		params.putBoolean(OpenPgpConstants.PARAMS_REQUEST_ASCII_ARMOR, true);
		long[] keyIds = { keyId };
		params.putLongArray(OpenPgpConstants.PARAMS_KEY_IDS, keyIds);

		InputStream is = new ByteArrayInputStream(message.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Bundle result = api.encrypt(params, is, os);
		StringBuilder encryptedMessageBody = new StringBuilder();
		String[] lines = os.toString().split("\n");
		for (int i = 3; i < lines.length - 1; ++i) {
			encryptedMessageBody.append(lines[i].trim());
		}
		return encryptedMessageBody.toString();
	}

	public long fetchKeyId(String status, String signature)
			throws OpenPgpException {
		StringBuilder pgpSig = new StringBuilder();
		pgpSig.append("-----BEGIN PGP SIGNED MESSAGE-----");
		pgpSig.append('\n');
		pgpSig.append("Hash: SHA1");
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
		Bundle params = new Bundle();
		params.putBoolean(OpenPgpConstants.PARAMS_REQUEST_ASCII_ARMOR, true);
		InputStream is = new ByteArrayInputStream(pgpSig.toString().getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Bundle result = api.decryptAndVerify(params, is, os);
		switch (result.getInt(OpenPgpConstants.RESULT_CODE)) {
		case OpenPgpConstants.RESULT_CODE_SUCCESS:
			OpenPgpSignatureResult sigResult = result
					.getParcelable(OpenPgpConstants.RESULT_SIGNATURE);
			return sigResult.getKeyId();
		case OpenPgpConstants.RESULT_CODE_USER_INTERACTION_REQUIRED:
			break;
		case OpenPgpConstants.RESULT_CODE_ERROR:
			throw new OpenPgpException(
					(OpenPgpError) result
							.getParcelable(OpenPgpConstants.RESULT_ERRORS));
		}
		return 0;
	}

	public String generateSignature(String status)
			throws UserInputRequiredException {
		Bundle params = new Bundle();
		params.putBoolean(OpenPgpConstants.PARAMS_REQUEST_ASCII_ARMOR, true);
		InputStream is = new ByteArrayInputStream(status.getBytes());
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		Bundle result = api.sign(params, is, os);
		StringBuilder signatureBuilder = new StringBuilder();
		switch (result.getInt(OpenPgpConstants.RESULT_CODE)) {
		case OpenPgpConstants.RESULT_CODE_SUCCESS:
			String[] lines = os.toString().split("\n");
			for (int i = 7; i < lines.length - 1; ++i) {
				signatureBuilder.append(lines[i].trim());
			}
			break;
		case OpenPgpConstants.RESULT_CODE_USER_INTERACTION_REQUIRED:
			UserInputRequiredException exception = new UserInputRequiredException(
					(PendingIntent) result
							.getParcelable(OpenPgpConstants.RESULT_INTENT));
			throw exception;
		case OpenPgpConstants.RESULT_CODE_ERROR:
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
