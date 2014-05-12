package eu.siacs.conversations.ui;

import android.app.PendingIntent;

public interface UiCallback {
	public void success();
	public void error(int errorCode);
	public void userInputRequried(PendingIntent pi);
}
