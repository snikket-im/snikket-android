package eu.siacs.conversations.services;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceIdService;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class InstanceIdService extends FirebaseInstanceIdService {

	@Override
	public void onTokenRefresh() {
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
			Log.e(Config.LOGTAG,"InstanceIdService is not allowed to start service");
		}
	}
}
