package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class EventReceiver extends BroadcastReceiver {

    public static final String SETTING_ENABLED_ACCOUNTS = "enabled_accounts";
    public static final String EXTRA_NEEDS_FOREGROUND_SERVICE = "needs_foreground_service";

    @Override
    public void onReceive(final Context context, final Intent originalIntent) {
        final Intent intentForService = new Intent(context, XmppConnectionService.class);
        if (originalIntent.getAction() != null) {
            intentForService.setAction(originalIntent.getAction());
        } else {
            intentForService.setAction("other");
        }
        final String action = originalIntent.getAction();
        if (action.equals("ui") || hasEnabledAccounts(context)) {
            try {
                if (Compatibility.runsAndTargetsTwentySix(context)) {
                    intentForService.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
                    ContextCompat.startForegroundService(context, intentForService);
                } else {
                    context.startService(intentForService);
                }
            } catch (RuntimeException e) {
                Log.d(Config.LOGTAG, "EventReceiver was unable to start service");
            }
        } else {
            Log.d(Config.LOGTAG, "EventReceiver ignored action " + intentForService.getAction());
        }
    }

    public static boolean hasEnabledAccounts(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTING_ENABLED_ACCOUNTS, true);
    }

}
