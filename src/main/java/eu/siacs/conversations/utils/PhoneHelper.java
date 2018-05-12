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

	public static void loadPhoneContacts(Context context, final OnPhoneContactsLoadedListener listener) {
		final List<Bundle> phoneContacts = new ArrayList<>();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
				&& context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
			listener.onPhoneContactsLoaded(phoneContacts);
			return;
		}
		final String[] PROJECTION = new String[]{ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_URI,
				ContactsContract.Data.LOOKUP_KEY,
				ContactsContract.CommonDataKinds.Im.DATA};

		final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
				+ ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
				+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
				+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
				+ "\")";

		CursorLoader mCursorLoader = new NotThrowCursorLoader(context,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, (arg0, c) -> {
			if (c != null) {
				while (c.moveToNext()) {
					Bundle contact = new Bundle();
					contact.putInt("phoneid", c.getInt(c.getColumnIndex(ContactsContract.Data._ID)));
					contact.putString("displayname", c.getString(c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
					contact.putString("photouri", c.getString(c.getColumnIndex(ContactsContract.Data.PHOTO_URI)));
					contact.putString("lookup", c.getString(c.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));
					contact.putString("jid", c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
					phoneContacts.add(contact);
				}
				c.close();
			}

			if (listener != null) {
				listener.onPhoneContactsLoaded(phoneContacts);
			}
		});
		try {
			mCursorLoader.startLoading();
		} catch (RejectedExecutionException e) {
			if (listener != null) {
				listener.onPhoneContactsLoaded(phoneContacts);
			}
		}
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

	private static class NotThrowCursorLoader extends CursorLoader {

		private NotThrowCursorLoader(Context c, Uri u, String[] p, String s, String[] sa, String so) {
			super(c, u, p, s, sa, so);
		}

		@Override
		public Cursor loadInBackground() {

			try {
				return (super.loadInBackground());
			} catch (Throwable e) {
				return (null);
			}
		}

	}
}
