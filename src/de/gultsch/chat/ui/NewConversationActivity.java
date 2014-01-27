package de.gultsch.chat.ui;

import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.persistance.DatabaseBackend;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.database.Cursor;

public class NewConversationActivity extends XmppActivity {

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
	protected static final String DEFAULT_PROFILE_PHOTO = "android.resource://de.gultsch.chat/" + R.drawable.ic_profile;

	protected View getViewForContact(Contact contact) {
		LayoutInflater  inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = (View) inflater.inflate(R.layout.contact,null);
		((TextView) view.findViewById(R.id.contact_display_name)).setText(contact.getDisplayName());
		((TextView) view.findViewById(R.id.contact_jid)).setText(contact.getJid());
		((ImageView) view.findViewById(R.id.contact_photo)).setImageURI(contact.getProfilePhoto());
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
				
				
				Account account = new Account();
				
				Conversation conversation = xmppConnectionService.findOrCreateConversation(account, clickedContact);
				
				Intent viewConversationIntent = new Intent(v.getContext(),ConversationActivity.class);
				viewConversationIntent.setAction(Intent.ACTION_VIEW);
				viewConversationIntent.putExtra(ConversationActivity.CONVERSATION, conversation.getUuid());
				viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);
				viewConversationIntent.setFlags(viewConversationIntent.getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(viewConversationIntent);
			}
		});
		return view;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		
		if (DatabaseBackend.getInstance(this).getConversationCount() < 1) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
		}
		
		setContentView(R.layout.activity_new_conversation);
		CursorLoader mCursorLoader = new CursorLoader(this,
				ContactsContract.Data.CONTENT_URI, PROJECTION, SELECTION, null,
				null);
		mCursorLoader.registerListener(0, new OnLoadCompleteListener<Cursor>() {

			@Override
			public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor) {
				while (cursor.moveToNext()) {
					String profilePhoto = cursor.getString(cursor.getColumnIndex(ContactsContract.Data.PHOTO_THUMBNAIL_URI));
					if (profilePhoto == null) {
						profilePhoto = DEFAULT_PROFILE_PHOTO;
					}
					Contact contact = new Contact(
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)),
							cursor.getString(cursor
									.getColumnIndex(ContactsContract.CommonDataKinds.Im.DATA)),
									profilePhoto
									);
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
			newContact = new Contact(name,search,DEFAULT_PROFILE_PHOTO);
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

	@Override
	void onBackendConnected() {
		
		getActionBar().setDisplayHomeAsUpEnabled(false);
		getActionBar().setHomeButtonEnabled(false);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.newconversation, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_accounts:
			startActivity(new Intent(this, ManageAccountActivity.class));
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
}
