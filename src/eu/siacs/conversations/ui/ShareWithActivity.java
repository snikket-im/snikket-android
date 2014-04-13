package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.utils.UIHelper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ShareWithActivity extends XmppActivity {
	
	private LinearLayout conversations;
	private LinearLayout contacts;
	
	private OnClickListener click = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			// TODO Auto-generated method stub
			
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.share_with);
		setTitle("Share with Conversation");
		
		contacts = (LinearLayout) findViewById(R.id.contacts);
		conversations = (LinearLayout) findViewById(R.id.conversations);
		
	}
	
	
	public View createContactView(String name, String msgTxt, Bitmap bm) {
		View view = (View) getLayoutInflater().inflate(R.layout.contact, null);
		view.setBackgroundResource(R.drawable.greybackground);
		TextView contactName =(TextView) view.findViewById(R.id.contact_display_name);
		contactName.setText(name);
		TextView msg = (TextView) view.findViewById(R.id.contact_jid);
		msg.setText(msgTxt);
		ImageView imageView = (ImageView) view.findViewById(R.id.contact_photo);
		imageView.setImageBitmap(bm);
		return view;
	}
	
	
	
	@Override
	void onBackendConnected() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
		
		Set<String> displayedContacts = new HashSet<String>();
		conversations.removeAllViews();
		List<Conversation> convList = xmppConnectionService.getConversations();
		Collections.sort(convList, new Comparator<Conversation>() {
			@Override
			public int compare(Conversation lhs, Conversation rhs) {
				return (int) (rhs.getLatestMessage().getTimeSent() - lhs.getLatestMessage().getTimeSent());
			}
		});
		for(final Conversation conversation : convList) {
			View view = createContactView(conversation.getName(useSubject),
					conversation.getLatestMessage().getBody().trim(),
					UIHelper.getContactPicture(conversation, 48,
						this.getApplicationContext(), false));
			view.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					 String sharedText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
					 switchToConversation(conversation, sharedText);
					 finish();
				}
			});
			conversations.addView(view);
			if (conversation.getContact() != null) {
				displayedContacts.add(conversation.getContact().getUuid());
			}
		}
		contacts.removeAllViews();
		final List<Contact> contactsList = new ArrayList<Contact>();
		for(Account account : xmppConnectionService.getAccounts()) {
			for(final Contact contact : xmppConnectionService.getRoster(account)) {
				if (!displayedContacts.contains(contact.getUuid())) {
					contactsList.add(contact);
				}
			}
		}
		
		Collections.sort(contactsList, new Comparator<Contact>() {
			@Override
			public int compare(Contact lhs, Contact rhs) {
				return lhs.getDisplayName().compareToIgnoreCase(rhs.getDisplayName());
			}
		});
		
		for(int i = 0; i < contactsList.size(); ++i) {
			final Contact con = contactsList.get(i);
			View view = createContactView(con.getDisplayName(), con.getJid(), 
					UIHelper.getContactPicture(con, 48, this.getApplicationContext(), false));
			view.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					 String sharedText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
					 Conversation conversation = xmppConnectionService.findOrCreateConversation(con.getAccount(), con.getJid(), false);
					 switchToConversation(conversation, sharedText);
					 finish();
				}
			});
			contacts.addView(view);
		}
	}

}
