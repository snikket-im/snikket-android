package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.URLDecoder;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.ui.StartConversationActivity;
import eu.siacs.conversations.utils.SignupUtils;

public class InstallReferrerService extends BroadcastReceiver {

    public static final String INSTALL_REFERRER_BROADCAST_ACTION = "eu.siacs.conversations.install_referrer";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final String referrer = intent == null ? null : intent.getStringExtra("referrer");
        if (referrer == null) {
            Log.d(Config.LOGTAG, "received empty referrer");
            return;
        }
        try {
            final String decoded = URLDecoder.decode(referrer, "UTF-8");
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            preferences.edit().putString(SignupUtils.INSTALL_REFERRER, decoded).apply();
            Log.d(Config.LOGTAG, "stored referrer: " + decoded);
            final Intent broadcastIntent = new Intent(INSTALL_REFERRER_BROADCAST_ACTION);
            broadcastIntent.putExtra(StartConversationActivity.EXTRA_INVITE_URI, decoded);
            context.sendBroadcast(broadcastIntent);
        } catch (final Exception e) {
            Log.d(Config.LOGTAG, "unable to process referrer", e);
        }
    }
}
