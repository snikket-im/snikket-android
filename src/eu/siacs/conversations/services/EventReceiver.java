package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class EventReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		if ((intent.getAction() != null)
				&& (intent.getAction()
						.equals("android.intent.action.BOOT_COMPLETED"))) {

		}
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null
				&& activeNetwork.isConnected();
		mIntentForService.putExtra("has_internet", isConnected);
		context.startService(mIntentForService);
	}

}
