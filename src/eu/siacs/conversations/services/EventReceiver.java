package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class EventReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		if ((intent.getAction() != null)
				&& (intent.getAction()
						.equals("android.intent.action.BOOT_COMPLETED"))) {

		}
		context.startService(mIntentForService);
	}

}
