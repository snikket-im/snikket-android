package eu.siacs.conversations.ui;

import android.app.PendingIntent;

public interface UiCallback<T> {
	void success(T object);

	void error(int errorCode, T object);

	void userInputRequried(PendingIntent pi, T object);
}
