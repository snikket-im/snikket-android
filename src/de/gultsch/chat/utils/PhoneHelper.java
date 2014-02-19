package de.gultsch.chat.utils;

import java.util.Hashtable;

import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {
	
	public static void loadPhoneContacts(Context context, final OnPhoneContactsLoadedListener listener) {
		if (Looper.myLooper()==null) {
			Looper.prepare();
		}
		final Looper mLooper = Looper.myLooper();
		final Hashtable<String, Bundle> phoneContacts = new Hashtable<String, Bundle>();
		
		final String[] PROJECTION = new String[] {
				ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_THUMBNAIL_URI,
				ContactsContract.Data.LOOKUP_KEY,
				ContactsContract.CommonDataKinds.Im.DATA };

		final String SELECTION = "(" + ContactsContract.Data.MIMETYPE + "=\""
				+ ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
				+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
				+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
				+ "\")";
		
		CursorLoader mCursorLoader = new CursorLoader(context,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
				while (cursor.moveToNext()) {
					Bundle contact = new Bundle();
					contact.putInt("phoneid", cursor.getInt(cursor
							.getColumnIndex(ContactsContract.Data._ID)));
					contact.putString(
							"displayname",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
					contact.putString(
							"photouri",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
					contact.putString("lookup",cursor.getString(cursor
							.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));
					phoneContacts.put(
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
							contact);
				}
				if (listener!=null) {
					listener.onPhoneContactsLoaded(phoneContacts);
				}
				mLooper.quit();
			}
		});
		mCursorLoader.startLoading();
	}

	public static Uri getSefliUri(Activity activity) {
		String[] mProjection = new String[] { Profile._ID,
				Profile.PHOTO_THUMBNAIL_URI };
		Cursor mProfileCursor = activity.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor.getCount()==0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			return Uri.parse(mProfileCursor.getString(1));
		}
	}
}
