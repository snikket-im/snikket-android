package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.persistance.DatabaseBackend;

public class EventReceiver extends BroadcastReceiver {

	public static final String SETTING_ENABLED_ACCOUNTS = "enabled_accounts";

	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context, XmppConnectionService.class);
		if (intent.getAction() != null) {
			mIntentForService.setAction(intent.getAction());
		} else {
			mIntentForService.setAction("other");
		}
		final String action = intent.getAction();
		if (action.equals("ui") || hasEnabledAccounts(context)) {
			context.startService(mIntentForService);
		} else {
			Log.d(Config.LOGTAG,"EventReceiver ignored action "+mIntentForService.getAction());
		}
	}

	public boolean hasEnabledAccounts(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTING_ENABLED_ACCOUNTS,true);
	}

}
