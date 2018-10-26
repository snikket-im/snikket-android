package eu.siacs.conversations.android;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.Collections;
import java.util.HashMap;

import eu.siacs.conversations.Config;
import rocks.xmpp.addr.Jid;

public class JabberIdContact extends AbstractPhoneContact {

    private final Jid jid;

    private JabberIdContact(Cursor cursor) throws IllegalArgumentException {
        super(cursor);
        try {
            this.jid = Jid.of(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Jid getJid() {
        return jid;
    }

    public static void load(Context context, OnPhoneContactsLoaded<JabberIdContact> callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            callback.onPhoneContactsLoaded(Collections.emptyList());
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
        final Cursor cursor;
        try {
            cursor = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null, null);
        } catch (Exception e) {
            callback.onPhoneContactsLoaded(Collections.emptyList());
            return;
        }
        final HashMap<Jid, JabberIdContact> contacts = new HashMap<>();
        while (cursor != null && cursor.moveToNext()) {
            try {
                final JabberIdContact contact = new JabberIdContact(cursor);
                final JabberIdContact preexisting = contacts.put(contact.getJid(), contact);
                if (preexisting == null || preexisting.rating() < contact.rating()) {
                    contacts.put(contact.getJid(), contact);
                }
            } catch (IllegalArgumentException e) {
                Log.d(Config.LOGTAG,"unable to create jabber id contact");
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        callback.onPhoneContactsLoaded(contacts.values());
    }
}
