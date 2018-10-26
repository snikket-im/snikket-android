package eu.siacs.conversations.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.CursorLoader;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

public class PhoneHelper {

	@SuppressLint("HardwareIds")
	public static String getAndroidId(Context context) {
		return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
	}

	public static Uri getProfilePictureUri(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			return null;
		}
		final String[] projection = new String[]{Profile._ID, Profile.PHOTO_URI};
		final Cursor cursor;
		try {
			cursor = context.getContentResolver().query(Profile.CONTENT_URI, projection, null, null, null);
		} catch (Throwable e) {
			return null;
		}
		if (cursor == null) {
			return null;
		}
		final String uri = cursor.moveToFirst() ? cursor.getString(1) : null;
		cursor.close();
		return uri == null ? null : Uri.parse(uri);
	}

	public static String getVersionName(Context context) {
		final String packageName = context == null ? null : context.getPackageName();
		if (packageName != null) {
			try {
				return context.getPackageManager().getPackageInfo(packageName, 0).versionName;
			} catch (final PackageManager.NameNotFoundException | RuntimeException e) {
				return "unknown";
			}
		} else {
			return "unknown";
		}
	}
}
