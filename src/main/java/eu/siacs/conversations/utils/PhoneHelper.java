package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Profile;

public class PhoneHelper {

	public static void loadPhoneContacts(Context context,final List<Bundle> phoneContacts, final OnPhoneContactsLoadedListener listener) {
		final String[] PROJECTION = new String[] { ContactsContract.Data._ID,
				ContactsContract.Data.DISPLAY_NAME,
				ContactsContract.Data.PHOTO_URI,
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
				if (cursor == null) {
					return;
				}
				while (cursor.moveToNext()) {
					Bundle contact = new Bundle();
					contact.putInt("phoneid", cursor.getInt(cursor
							.getColumnIndex(ContactsContract.Data._ID)));
					contact.putString(
							"displayname",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)));
					contact.putString("photouri", cursor.getString(cursor
							.getColumnIndex(ContactsContract.Data.PHOTO_URI)));
					contact.putString("lookup", cursor.getString(cursor
							.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)));

					contact.putString(
							"jid",
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
					phoneContacts.add(contact);
				}
				if (listener != null) {
					listener.onPhoneContactsLoaded(phoneContacts);
				}
				cursor.close();
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

	public static Uri getSefliUri(Context context) {
		String[] mProjection = new String[] { Profile._ID, Profile.PHOTO_URI };
		Cursor mProfileCursor = context.getContentResolver().query(
				Profile.CONTENT_URI, mProjection, null, null, null);

		if (mProfileCursor == null || mProfileCursor.getCount() == 0) {
			return null;
		} else {
			mProfileCursor.moveToFirst();
			String uri = mProfileCursor.getString(1);
			mProfileCursor.close();
			if (uri == null) {
				return null;
			} else {
				return Uri.parse(uri);
			}
		}
	}
}
