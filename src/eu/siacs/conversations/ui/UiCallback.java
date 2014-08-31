package eu.siacs.conversations.ui;

import android.app.PendingIntent;

public interface UiCallback<T> {
	public void success(T object);

	public void error(int errorCode, T object);

	public void userInputRequried(PendingIntent pi, T object);
}
