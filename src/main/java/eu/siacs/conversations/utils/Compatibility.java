package eu.siacs.conversations.utils;

import static eu.siacs.conversations.receiver.SystemEventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.BoolRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class Compatibility {

    public static boolean hasStoragePermission(final Context context) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean s() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private static boolean runsTwentyFour() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    public static boolean runsTwentySix() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }

    public static boolean twentyEight() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    private static boolean getBooleanPreference(Context context, String name, @BoolRes int res) {
        return getPreferences(context).getBoolean(name, context.getResources().getBoolean(res));
    }

    private static SharedPreferences getPreferences(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static boolean targetsTwentySix(Context context) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            final ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(context.getPackageName(), 0);
            return applicationInfo.targetSdkVersion >= 26;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return true; // when in doubt…
        }
    }

    private static boolean targetsTwentyFour(Context context) {
        try {
            final PackageManager packageManager = context.getPackageManager();
            final ApplicationInfo applicationInfo =
                    packageManager.getApplicationInfo(context.getPackageName(), 0);
            return applicationInfo.targetSdkVersion >= 24;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return true; // when in doubt…
        }
    }

    public static boolean runsAndTargetsTwentySix(Context context) {
        return runsTwentySix() && targetsTwentySix(context);
    }

    public static boolean runsAndTargetsTwentyFour(Context context) {
        return runsTwentyFour() && targetsTwentyFour(context);
    }

    public static boolean keepForegroundService(Context context) {
        return runsAndTargetsTwentySix(context)
                || getBooleanPreference(
                        context,
                        AppSettings.KEEP_FOREGROUND_SERVICE,
                        R.bool.enable_foreground_service);
    }


    public static void startService(final Context context, final Intent intent) {
        try {
            if (Compatibility.runsAndTargetsTwentySix(context)) {
                intent.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (final RuntimeException e) {
            Log.d(
                    Config.LOGTAG,
                    context.getClass().getSimpleName() + " was unable to start service");
        }
    }

    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    public static boolean hasFeatureCamera(final Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static int getRestrictBackgroundStatus(
            @NonNull final ConnectivityManager connectivityManager) {
        try {
            return connectivityManager.getRestrictBackgroundStatus();
        } catch (final Exception e) {
            Log.d(
                    Config.LOGTAG,
                    "platform bug detected. Unable to get restrict background status",
                    e);
            return ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public static boolean isActiveNetworkMetered(
            @NonNull final ConnectivityManager connectivityManager) {
        try {
            return connectivityManager.isActiveNetworkMetered();
        } catch (final RuntimeException e) {
            // when in doubt better assume it's metered
            return true;
        }
    }

    public static Bundle pgpStartIntentSenderOptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle();
        } else {
            return null;
        }
    }
}
