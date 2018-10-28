package eu.siacs.conversations.android;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

public abstract class AbstractPhoneContact {

    private final Uri lookupUri;
    private final String displayName;
    private final String photoUri;


    AbstractPhoneContact(Cursor cursor) {
        int phoneId = cursor.getInt(cursor.getColumnIndex(ContactsContract.Data._ID));
        String lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY));
        this.lookupUri = ContactsContract.Contacts.getLookupUri(phoneId, lookupKey);
        this.displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
        this.photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI));
    }

    public Uri getLookupUri() {
        return lookupUri;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhotoUri() {
        return photoUri;
    }


    public int rating() {
        return (TextUtils.isEmpty(displayName) ? 0 : 2) + (TextUtils.isEmpty(photoUri) ? 0 : 1);
    }
}
