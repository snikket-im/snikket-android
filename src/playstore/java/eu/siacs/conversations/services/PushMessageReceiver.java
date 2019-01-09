package eu.siacs.conversations.services;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class PushMessageReceiver extends FirebaseMessagingService {

	@Override
	public void onMessageReceived(RemoteMessage message) {
		if (!EventReceiver.hasEnabledAccounts(this)) {
			Log.d(Config.LOGTAG,"PushMessageReceiver ignored message because no accounts are enabled");
			return;
		}
		final Map<String, String> data = message.getData();
		final Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_FCM_MESSAGE_RECEIVED);
		intent.putExtra("account", data.get("account"));
		try {
			if (Compatibility.runsAndTargetsTwentySix(this)) {
				intent.putExtra(EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, true);
				ContextCompat.startForegroundService(this, intent);
			} else {
				startService(intent);
			}
		} catch (IllegalStateException e) {
			Log.e(Config.LOGTAG,"PushMessageReceiver is not allowed to start service after receiving message");
		}
	}

	@Override
	public void onNewToken(String token) {
		if (!EventReceiver.hasEnabledAccounts(this)) {
			Log.d(Config.LOGTAG,"PushMessageReceiver ignored new token because no accounts are enabled");
			return;
		}
		final Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
		try {
			if (Compatibility.runsAndTargetsTwentySix(this)) {
				intent.putExtra(EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, true);
				ContextCompat.startForegroundService(this, intent);
			} else {
				startService(intent);
			}
		} catch (IllegalStateException e) {
			Log.e(Config.LOGTAG,"PushMessageReceiver is not allowed to start service after receiving new token");
		}
	}

}
