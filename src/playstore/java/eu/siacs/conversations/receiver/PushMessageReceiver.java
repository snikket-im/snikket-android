package eu.siacs.conversations.receiver;

import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.Compatibility;

public class PushMessageReceiver extends FirebaseMessagingService {

	@Override
	public void onMessageReceived(@NonNull final RemoteMessage message) {
		if (!SystemEventReceiver.hasEnabledAccounts(this)) {
			Log.d(Config.LOGTAG,"PushMessageReceiver ignored message because no accounts are enabled");
			return;
		}
		final Map<String, String> data = message.getData();
		final Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_FCM_MESSAGE_RECEIVED);
		intent.putExtra("account", data.get("account"));
		Compatibility.startService(this, intent);
	}

	@Override
	public void onNewToken(@NonNull final String token) {
		if (!SystemEventReceiver.hasEnabledAccounts(this)) {
			Log.d(Config.LOGTAG,"PushMessageReceiver ignored new token because no accounts are enabled");
			return;
		}
		final Intent intent = new Intent(this, XmppConnectionService.class);
		intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
		Compatibility.startService(this, intent);
	}

}
