package de.gultsch.chat.ui;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.gultsch.chat.Contact;
import de.gultsch.chat.R;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;

public class NewConversationActivity extends Activity {

	final protected LinkedHashMap<Contact, View> availablePhoneContacts = new LinkedHashMap<Contact, View>();
	final protected LinkedHashMap<Contact, View> availableJabberContacts = new LinkedHashMap<Contact, View>();
	protected View newContactView;
	protected Contact newContact;
	
	public static final Pattern VALID_JID = 
		    Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

	static final String[] PROJECTION = new String[] {
			ContactsContract.Data.CONTACT_ID,
			ContactsContract.Data.DISPLAY_NAME,
			ContactsContract.Data.PHOTO_THUMBNAIL_URI,
			ContactsContract.CommonDataKinds.Im.DATA };

	// This is the select criteria
	static final String SELECTION = "(" + ContactsContract.Data.MIMETYPE
			+ "=\"" + ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
			+ "\") AND (" + ContactsContract.CommonDataKinds.Im.PROTOCOL
			+ "=\"" + ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER
			+ "\")";

	protected View getViewForContact(Contact contact) {
		LayoutInflater  inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = (View) inflater.inflate(R.layout.contact,null);
		((TextView) view.findViewById(R.id.contact_display_name)).setText(contact.getDisplayName());
		((TextView) view.findViewById(R.id.contact_jid)).setText(contact.getJid());
		if (contact.getProfilePhoto() != null) {
		((ImageView) view.findViewById(R.id.contact_photo)).setImageURI(contact.getProfilePhoto());
		}
		view.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				Contact clickedContact = null;
				for(Entry<Contact, View> entry  : availablePhoneContacts.entrySet()) {
					if (entry.getValue() == v) {
						clickedContact = entry.getKey();
						break;
					}
				}
				for(Entry<Contact, View> entry  : availableJabberContacts.entrySet()) {
					if (entry.getValue() == v) {
						clickedContact = entry.getKey();
						break;
					}
				}
				if (newContactView==v) {
					clickedContact = newContact;
				}
				Log.d("gultsch","clicked on "+clickedContact.getDisplayName());
				Intent startConversationIntent = new Intent(v.getContext(),ConversationActivity.class);
				startConversationIntent.setAction(Intent.ACTION_VIEW);
				startConversationIntent.putExtra(ConversationActivity.CONVERSATION_CONTACT, clickedContact);
				startConversationIntent.setType(ConversationActivity.START_CONVERSATION);
				startConversationIntent.setFlags(startConversationIntent.getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(startConversationIntent);
			}
		});
		return view;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_new_conversation);
		CursorLoader mCursorLoader = new CursorLoader(this,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
				while (cursor.moveToNext()) {
					Contact contact = new Contact(
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)),
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
									cursor.getString(cursor
											.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI)));
					View contactView = getViewForContact(contact);
					availablePhoneContacts.put(contact, getViewForContact(contact));
					((LinearLayout) findViewById(R.id.phone_contacts)).addView(contactView);
				}
				updateAvailableContacts();
			}
		});
		mCursorLoader.startLoading();

		((TextView) findViewById(R.id.new_conversation_search)).addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateAvailableContacts();
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub
				
			}
		});
		
	}
	
	protected void updateAvailableContacts() {
		String search = ((TextView) findViewById(R.id.new_conversation_search)).getText().toString();
		
		LinearLayout phoneContacts = (LinearLayout) findViewById(R.id.phone_contacts);
		filterAvailableContacts(phoneContacts,this.availablePhoneContacts,search);
		
		if (phoneContacts.getChildCount() == 0) {
			findViewById(R.id.phone_contacts_header).setVisibility(View.GONE);
		} else {
			findViewById(R.id.phone_contacts_header).setVisibility(View.VISIBLE);
		}
		
		LinearLayout jabberContacts = (LinearLayout) findViewById(R.id.jabber_contacts);
		filterAvailableContacts(jabberContacts,this.availableJabberContacts,search);
		if (jabberContacts.getChildCount() == 0) {
			findViewById(R.id.jabber_contacts_header).setVisibility(View.GONE);
		} else {
			findViewById(R.id.jabber_contacts_header).setVisibility(View.VISIBLE);
		}
		
		LinearLayout createNewContact = (LinearLayout) findViewById(R.id.create_new_contact);
		Matcher matcher = VALID_JID.matcher(search);
		if (matcher.find()) {
			createNewContact.removeAllViews();
			String name = search.split("@")[0];
			newContact = new Contact(name,search,null);
			newContactView = getViewForContact(newContact);
			newContactView.findViewById(R.id.contact_divider).setVisibility(View.GONE);
			createNewContact.addView(newContactView);
			createNewContact.setVisibility(View.VISIBLE);
			((TextView) findViewById(R.id.new_contact_header)).setVisibility(View.VISIBLE);
		} else {
			createNewContact.setVisibility(View.GONE);
			((TextView) findViewById(R.id.new_contact_header)).setVisibility(View.GONE);
		}
	}

	private void filterAvailableContacts(
			LinearLayout layout, LinkedHashMap<Contact, View> contacts, String search) {
		layout.removeAllViews();
		for(Entry<Contact, View> entry  : contacts.entrySet()) {		
			
			if (entry.getKey().match(search)) {
				entry.getValue().setVisibility(View.VISIBLE);
				entry.getValue().findViewById(R.id.contact_divider).setVisibility(View.VISIBLE);
				layout.addView(entry.getValue());
			}
		}
		int contactsCount = layout.getChildCount();
		if (contactsCount>=1) {
			View lastContact = layout.getChildAt(contactsCount - 1);
			lastContact.findViewById(R.id.contact_divider).setVisibility(View.GONE);
		}
	}
}
