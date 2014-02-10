package de.gultsch.chat.utils;

import java.util.Hashtable;

import android.os.Bundle;

public interface OnPhoneContactsLoadedListener {
	public void onPhoneContactsLoaded(Hashtable<String, Bundle> phoneContacts);
}
