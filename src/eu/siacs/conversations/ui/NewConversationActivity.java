package eu.siacs.conversations.ui;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.security.SecureRandom;
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
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
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
	private List<Contact> selectedContacts = new ArrayList<Contact>();
	
	private boolean isActionMode = false;
	private ActionMode actionMode = null;
	private AbsListView.MultiChoiceModeListener actionModeCallback = new AbsListView.MultiChoiceModeListener() {
		
		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			menu.clear();
			MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.newconversation_context, menu);
	        SparseBooleanArray checkedItems = contactsView.getCheckedItemPositions();
	        selectedContacts.clear();
	        for(int i = 0; i < aggregatedContacts.size(); ++i) {
	        	if (checkedItems.get(i, false)) {
	        		selectedContacts.add(aggregatedContacts.get(i));
	        	}
	        }
	        if (selectedContacts.size() == 0) {
	        	menu.findItem(R.id.action_start_conversation).setVisible(false);
	        	menu.findItem(R.id.action_contact_details).setVisible(false);
	        } else if (selectedContacts.size() == 1) {
	        	menu.findItem(R.id.action_start_conversation).setVisible(true);
	        	menu.findItem(R.id.action_contact_details).setVisible(true);
	        } else {
	        	menu.findItem(R.id.action_start_conversation).setVisible(true);
	        	menu.findItem(R.id.action_contact_details).setVisible(false);
	        }
			return true;
		}
		
		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// TODO Auto-generated method stub
			
		}
		
		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			return true;
		}
		
		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.action_start_conversation:
				if (selectedContacts.size() == 1) {
					startConversation(selectedContacts.get(0));
				} else {
					startConference();
				}
				break;
			case R.id.action_contact_details:
				Intent intent = new Intent(getApplicationContext(),ContactDetailsActivity.class);
				intent.setAction(ContactDetailsActivity.ACTION_VIEW_CONTACT);
				intent.putExtra("uuid", selectedContacts.get(0).getUuid());
				startActivity(intent);
				break;
			default:
				break;
			}
			// TODO Auto-generated method stub
			return false;
		}
		
		@Override
		public void onItemCheckedStateChanged(ActionMode mode, int position,
				long id, boolean checked) {
		}
	};
	
	private void startConference() {
		if (accounts.size()>1) {
			getAccountChooser(new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					startConference(accounts.get(which), selectedContacts);
				}
				}).show();
		} else {
			startConference(accounts.get(0), selectedContacts);
		}
		
	}
	
	private void startConference(Account account, List<Contact> contacts) {
		SecureRandom random = new SecureRandom();
		String mucName = new BigInteger(100,random).toString(32);
		String serverName = account.getXmppConnection().getMucServer();
		String jid = mucName+"@"+serverName;
		Conversation conversation = xmppConnectionService.findOrCreateConversation(account, jid , true);
		StringBuilder subject = new StringBuilder();
		for(int i = 0; i < selectedContacts.size(); ++i) {
			if (i+1!=selectedContacts.size()) {
				subject.append(selectedContacts.get(i).getDisplayName()+", ");
			} else {
				subject.append(selectedContacts.get(i).getDisplayName());
			}
		}
		xmppConnectionService.sendConversationSubject(conversation, subject.toString());
		xmppConnectionService.inviteToConference(conversation, contacts);
		switchToConversation(conversation, null);
	}

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
	
	private OnItemLongClickListener onLongClickListener = new OnItemLongClickListener() {

		@Override
		public boolean onItemLongClick(AdapterView<?> arg0, View view,
				int position, long arg3) {
			if (!isActionMode) {
				contactsView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
				contactsView.setItemChecked(position,true);
				actionMode = contactsView.startActionMode(actionModeCallback);
			}
			return true;
		}
	};

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
		contactsView.setMultiChoiceModeListener(actionModeCallback);
		contactsView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, final View view,
					int pos, long arg3) {
				if (!isActionMode) {
					Contact clickedContact = aggregatedContacts.get(pos);
					startConversation(clickedContact);
					
				} else {
					actionMode.invalidate();
				}
			}
		});
		contactsView.setOnItemLongClickListener(this.onLongClickListener);
	}
	
	public void startConversation(final Contact contact) {
		if ((contact.getAccount()==null)&&(accounts.size()>1)) {
			getAccountChooser(new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					contact.setAccount(accounts.get(which));
					showIsMucDialogIfNeeded(contact);
				}
				}).show();
		} else {
			if (contact.getAccount()==null) {
				contact.setAccount(accounts.get(0));
			}
			showIsMucDialogIfNeeded(contact);
		}
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
	
	@Override
	public void onActionModeStarted(ActionMode mode) {
		super.onActionModeStarted(mode);
		this.isActionMode = true;
		search.setEnabled(false);
	}
	
	@Override
	public void onActionModeFinished(ActionMode mode) {
		super.onActionModeFinished(mode);
		this.isActionMode = false;
		contactsView.clearChoices();
		contactsView.requestLayout();
		contactsView.post(new Runnable() {
            @Override
            public void run() {
                contactsView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            }
        });
		search.setEnabled(true);
	}
	
}
