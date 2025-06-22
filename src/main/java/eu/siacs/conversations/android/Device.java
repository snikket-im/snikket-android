package eu.siacs.conversations.android;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import eu.siacs.conversations.Config;

public class Device {

    private final Context context;

    public Device(final Context context) {
        this.context = context;
    }

    public boolean isScreenLocked() {
        final var keyguardManager = context.getSystemService(KeyguardManager.class);
        final var powerManager = context.getSystemService(PowerManager.class);
        final var locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
        final boolean interactive;
        try {
            interactive = powerManager != null && powerManager.isInteractive();
        } catch (final Exception e) {
            return false;
        }
        return locked || !interactive;
    }

    public boolean isPhoneSilenced(final boolean vibrateIsSilent) {
        final var notificationManager = context.getSystemService(NotificationManager.class);
        final int filter =
                notificationManager == null
                        ? NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                        : notificationManager.getCurrentInterruptionFilter();
        final boolean notificationDnd = filter >= NotificationManager.INTERRUPTION_FILTER_PRIORITY;
        final var audioManager = context.getSystemService(AudioManager.class);
        final int ringerMode =
                audioManager == null
                        ? AudioManager.RINGER_MODE_NORMAL
                        : audioManager.getRingerMode();
        try {
            if (vibrateIsSilent) {
                return notificationDnd || ringerMode != AudioManager.RINGER_MODE_NORMAL;
            } else {
                return notificationDnd || ringerMode == AudioManager.RINGER_MODE_SILENT;
            }
        } catch (final Throwable throwable) {
            Log.d(Config.LOGTAG, "platform bug in isPhoneSilenced", throwable);
            return notificationDnd;
        }
    }

    public boolean isPhysicalDevice() {
        return !isEmulator();
    }

    private static boolean isEmulator() {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("sdk_gphone64_arm64")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator");
    }
}
