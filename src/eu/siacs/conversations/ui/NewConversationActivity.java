package eu.siacs.conversations.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.Validator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;

public class NewConversationActivity extends XmppActivity {

	protected List<Contact> rosterContacts = new ArrayList<Contact>();
	protected List<Contact> aggregatedContacts = new ArrayList<Contact>();
	protected ListView contactsView;
	protected ArrayAdapter<Contact> contactsAdapter;

	protected EditText search;
	protected String searchString = "";
	private TextView contactsHeader;
	private List<Account> accounts;

	protected void updateAggregatedContacts() {

		aggregatedContacts.clear();
		for (Contact contact : rosterContacts) {
			if (contact.match(searchString))
				aggregatedContacts.add(contact);
		}

		Collections.sort(aggregatedContacts, new Comparator<Contact>() {

			@SuppressLint("DefaultLocale")
			@Override
			public int compare(Contact lhs, Contact rhs) {
				return lhs.getDisplayName().toLowerCase()
						.compareTo(rhs.getDisplayName().toLowerCase());
			}
		});

		if (aggregatedContacts.size() == 0) {

			if (Validator.isValidJid(searchString)) {
				String name = searchString.split("@")[0];
				Contact newContact = new Contact(null, name, searchString, null);
				newContact.flagAsNotInRoster();
				aggregatedContacts.add(newContact);
				contactsHeader.setText("Create new contact");
			} else {
				contactsHeader.setText("Contacts");
			}
		} else {
			contactsHeader.setText("Contacts");
		}

		contactsAdapter.notifyDataSetChanged();
		contactsView.setScrollX(0);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_new_conversation);

		contactsHeader = (TextView) findViewById(R.id.contacts_header);

		search = (EditText) findViewById(R.id.new_conversation_search);
		search.addTextChangedListener(new TextWatcher() {

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				searchString = search.getText().toString();
				updateAggregatedContacts();
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

		contactsView = (ListView) findViewById(R.id.contactList);
		contactsAdapter = new ArrayAdapter<Contact>(getApplicationContext(),
				R.layout.contact, aggregatedContacts) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				Contact contact = getItem(position);
				if (view == null) {
					view = (View) inflater.inflate(R.layout.contact, null);
				}

				((TextView) view.findViewById(R.id.contact_display_name))
						.setText(getItem(position).getDisplayName());
				TextView contactJid = (TextView) view
						.findViewById(R.id.contact_jid);
				contactJid.setText(contact.getJid());
				ImageView imageView = (ImageView) view
						.findViewById(R.id.contact_photo);
					imageView.setImageBitmap(UIHelper.getContactPicture(contact,null,90,this.getContext()));
				return view;
			}
		};
		contactsView.setAdapter(contactsAdapter);
		final Activity activity = this;
		contactsView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, final View view,
					int pos, long arg3) {
				final Contact clickedContact = aggregatedContacts.get(pos);
				
				if ((clickedContact.getAccount()==null)&&(accounts.size()>1)) {
					getAccountChooser(new OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							clickedContact.setAccount(accounts.get(which));
							showIsMucDialogIfNeeded(clickedContact);
						}
						}).show();
				} else {
					if (clickedContact.getAccount()==null) {
						clickedContact.setAccount(accounts.get(0));
					}
					showIsMucDialogIfNeeded(clickedContact);
				}
			}
		});
		contactsView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
					int pos, long arg3) {
				Intent intent = new Intent(activity,ContactDetailsActivity.class);
				intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
				intent.putExtra("uuid", aggregatedContacts.get(pos).getUuid());
				startActivity(intent);
				return true;
			}
		});
	}
	
	protected AlertDialog getAccountChooser(OnClickListener listener) {
		String[] accountList = new String[accounts.size()];
		for (int i = 0; i < accounts.size(); ++i) {
		accountList[i] = accounts.get(i).getJid();
		}

		AlertDialog.Builder accountChooser = new AlertDialog.Builder(
		this);
		accountChooser.setTitle("Choose account");
		accountChooser.setItems(accountList, listener);
		return accountChooser.create();
	}
	
	public void showIsMucDialogIfNeeded(final Contact clickedContact) {
		if (clickedContact.couldBeMuc()) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle("Multi User Conference");
			dialog.setMessage("Are you trying to join a conference?");
			dialog.setPositiveButton("Yes", new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startConversation(clickedContact, clickedContact.getAccount(),true);
				}
			});
			dialog.setNegativeButton("No", new OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					startConversation(clickedContact, clickedContact.getAccount(),false);
				}
			});
			dialog.create().show();
		} else {
			startConversation(clickedContact, clickedContact.getAccount(),false);
		}
	}

	public void startConversation(Contact contact, Account account, boolean muc) {
		if (!contact.isInRoster()) {
			xmppConnectionService.createContact(contact);
		}
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(account, contact.getJid(), muc);

		switchToConversation(conversation,null);
	}

	@Override
	void onBackendConnected() {
		this.accounts = xmppConnectionService.getAccounts();
		if (Intent.ACTION_SENDTO.equals(getIntent().getAction())) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
			String jid;
			try {
				jid = URLDecoder.decode(getIntent().getData().getEncodedPath(),"UTF-8").split("/")[1];
			} catch (UnsupportedEncodingException e) {
				jid = null;
			}
			if (jid!=null) {
				final String finalJid = jid;
				if (this.accounts.size() > 1) {
					getAccountChooser(new OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Conversation conversation = xmppConnectionService.findOrCreateConversation(accounts.get(which), finalJid, false);
							switchToConversation(conversation,null);
							finish();
						}
					}).show();
				} else {
					Conversation conversation = xmppConnectionService.findOrCreateConversation(this.accounts.get(0), jid, false);
					switchToConversation(conversation,null);
					finish();
				}
			}
		}
		
		
		if (xmppConnectionService.getConversationCount() == 0) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
		}
		this.rosterContacts.clear();
		for (int i = 0; i < accounts.size(); ++i) {
			rosterContacts.addAll(xmppConnectionService.getRoster(accounts.get(i)));
		}
		updateAggregatedContacts();
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
		case R.id.action_refresh_contacts:
			refreshContacts();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void refreshContacts() {
		final ProgressBar progress = (ProgressBar) findViewById(R.id.progressBar1);
		final EditText searchBar = (EditText) findViewById(R.id.new_conversation_search);
		final TextView contactsHeader = (TextView) findViewById(R.id.contacts_header);
		final ListView contactList = (ListView) findViewById(R.id.contactList);
		searchBar.setVisibility(View.GONE);
		contactsHeader.setVisibility(View.GONE);
		contactList.setVisibility(View.GONE);
		progress.setVisibility(View.VISIBLE);
		this.accounts = xmppConnectionService.getAccounts();
		this.rosterContacts.clear();
		for (int i = 0; i < accounts.size(); ++i) {
			if (accounts.get(i).getStatus() == Account.STATUS_ONLINE) {
				xmppConnectionService.updateRoster(accounts.get(i),
						new OnRosterFetchedListener() {

							@Override
							public void onRosterFetched(
									final List<Contact> roster) {
								runOnUiThread(new Runnable() {

									@Override
									public void run() {
										rosterContacts.addAll(roster);
										progress.setVisibility(View.GONE);
										searchBar.setVisibility(View.VISIBLE);
										contactList.setVisibility(View.VISIBLE);
										contactList.setVisibility(View.VISIBLE);
										updateAggregatedContacts();
									}
								});
							}
						});
			}
		}
	}
}
