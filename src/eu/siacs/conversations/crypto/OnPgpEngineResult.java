package eu.siacs.conversations.crypto;

import org.openintents.openpgp.OpenPgpError;

import android.app.PendingIntent;

public interface OnPgpEngineResult {
	public void success();
	public void error(OpenPgpError openPgpError);
	public void userInputRequried(PendingIntent pi);
}
