package eu.siacs.conversations.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
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
                || context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
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
}
