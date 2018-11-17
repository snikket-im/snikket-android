package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;

import java.io.IOException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class MaintenanceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.d(Config.LOGTAG, "received intent in maintenance receiver");
		if ("eu.siacs.conversations.RENEW_INSTANCE_ID".equals(intent.getAction())) {
			renewInstanceToken(context);

		}
	}

	private void renewInstanceToken(final Context context) {
		new Thread(() -> {
			try {
				FirebaseInstanceId.getInstance().deleteInstanceId();
				final Intent intent = new Intent(context, XmppConnectionService.class);
				intent.setAction(XmppConnectionService.ACTION_FCM_TOKEN_REFRESH);
				if (Compatibility.runsAndTargetsTwentySix(context)) {
					intent.putExtra(EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, true);
					ContextCompat.startForegroundService(context, intent);
				} else {
					context.startService(intent);
				}
			} catch (IOException e) {
				Log.d(Config.LOGTAG, "unable to renew instance token", e);
			}
		}).start();

	}
}
