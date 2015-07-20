package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import eu.siacs.conversations.persistance.DatabaseBackend;

public class EventReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		if (intent.getAction() != null) {
			mIntentForService.setAction(intent.getAction());
		} else {
			mIntentForService.setAction("other");
		}
		if (intent.getAction().equals("ui")
				|| DatabaseBackend.getInstance(context).hasEnabledAccounts()) {
			context.startService(mIntentForService);
		}
	}

}
