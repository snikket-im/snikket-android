package eu.siacs.conversations.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;

import com.google.common.base.Strings;

import eu.siacs.conversations.services.QuickConversationsService;

public class PhoneHelper {

    @SuppressLint("HardwareIds")
    public static String getAndroidId(final Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public static Uri getProfilePictureUri(final Context context) {
        if (!QuickConversationsService.isContactListIntegration(context)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                                != PackageManager.PERMISSION_GRANTED)) {
            return null;
        }
        final String[] projection = new String[] {Profile._ID, Profile.PHOTO_URI};
        try (final Cursor cursor =
                context.getContentResolver()
                        .query(Profile.CONTENT_URI, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final var photoUri = cursor.getString(1);
                if (Strings.isNullOrEmpty(photoUri)) {
                    return null;
                }
                return Uri.parse(photoUri);
            }
        }
        return null;
    }

    public static boolean isEmulator() {
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
