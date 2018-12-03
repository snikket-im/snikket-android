package eu.siacs.conversations.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import eu.siacs.conversations.R;
import me.drakeet.support.toast.ToastCompat;

public class TorServiceUtils {

    private final static String URI_ORBOT = "org.torproject.android";
    private static final Uri ORBOT_PLAYSTORE_URI = Uri.parse("market://details?id=" + URI_ORBOT);
    private final static String ACTION_START_TOR = "org.torproject.android.START_TOR";

    public static boolean isOrbotInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(URI_ORBOT, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }


    public static void downloadOrbot(Activity activity, int requestCode) {
        final Intent intent = new Intent(Intent.ACTION_VIEW, ORBOT_PLAYSTORE_URI);
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            ToastCompat.makeText(activity, R.string.no_market_app_installed, ToastCompat.LENGTH_SHORT).show();
        }
    }

    public static void startOrbot(Activity activity, int requestCode) {
        final Intent launchIntent = new Intent(URI_ORBOT);
        launchIntent.setAction(ACTION_START_TOR);
        activity.startActivityForResult(launchIntent, requestCode);
    }
}
