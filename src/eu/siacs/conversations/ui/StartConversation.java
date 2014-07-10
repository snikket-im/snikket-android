package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.Spinner;
import android.widget.TextView;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.utils.KnownHostsAdapter;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.Validator;

public class StartConversation extends XmppActivity {

	private Tab mContactsTab;
	private Tab mConferencesTab;
	private ViewPager mViewPager;
	private SearchView mSearchView;

	private MyListFragment mContactsListFragment = new MyListFragment();
	private List<ListItem> contacts = new ArrayList<ListItem>();
	private ArrayAdapter<ListItem> mContactsAdapter;

	private MyListFragment mConferenceListFragment = new MyListFragment();
	private List<ListItem> conferences = new ArrayList<ListItem>();
	private ArrayAdapter<ListItem> mConferenceAdapter;
	
	private List<String> mActivatedAccounts = new ArrayList<String>();
	private List<String> mKnownHosts;

	private TabListener mTabListener = new TabListener() {

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			return;
		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			mViewPager.setCurrentItem(tab.getPosition());
			onTabChanged();
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {
			return;
		}
	};

	private ViewPager.SimpleOnPageChangeListener mOnPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			getActionBar().setSelectedNavigationItem(position);
			onTabChanged();
		}
	};
	private OnQueryTextListener mOnQueryTextListener = new OnQueryTextListener() {

		@Override
		public boolean onQueryTextSubmit(String query) {
			return true;
		}

		@Override
		public boolean onQueryTextChange(String newText) {
			filterContacts(newText);
			return true;
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_start_conversation);
		mViewPager = (ViewPager) findViewById(R.id.start_conversation_view_pager);
		ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		mContactsTab = actionBar.newTab().setText(R.string.contacts)
				.setTabListener(mTabListener);
		mConferencesTab = actionBar.newTab().setText(R.string.conferences)
				.setTabListener(mTabListener);
		actionBar.addTab(mContactsTab);
		actionBar.addTab(mConferencesTab);

		mViewPager.setOnPageChangeListener(mOnPageChangeListener);
		mViewPager.setAdapter(new FragmentPagerAdapter(getFragmentManager()) {

			@Override
			public int getCount() {
				return 2;
			}

			@Override
			public Fragment getItem(int position) {
				if (position == 0) {
					return mContactsListFragment;
				} else {
					return mConferenceListFragment;
				}
			}
		});

		mConferenceAdapter = new ListItemAdapter(conferences);
		mConferenceListFragment.setListAdapter(mConferenceAdapter);

		mContactsAdapter = new ListItemAdapter(contacts);
		mContactsListFragment.setListAdapter(mContactsAdapter);
		mContactsListFragment
				.setOnListItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
							int position, long arg3) {
						openConversationForContact(position);
					}
				});

	}

	protected void openConversationForContact(int position) {
		Contact contact = (Contact) contacts.get(position);
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(contact.getAccount(),
						contact.getJid(), false);
		switchToConversation(conversation, null, false);
	}
	
	protected void openDetailsForContact(int position) {
		Contact contact = (Contact) contacts.get(position);
		switchToContactDetails(contact);
	}
	
	protected void deleteContact(int position) {
		Contact contact = (Contact) contacts.get(position);
		xmppConnectionService.deleteContactOnServer(contact);
		filterContacts(null);
	}
	
	protected void showCreateContactDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.create_contact);
		View dialogView = getLayoutInflater().inflate(R.layout.create_contact_dialog, null);
		final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, mKnownHosts));
		populateAccountSpinner(spinner);
		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.create, null);
		final AlertDialog dialog = builder.create();
		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if (Validator.isValidJid(jid.getText().toString())) {
					String accountJid = (String) spinner.getSelectedItem();
					String contactJid = jid.getText().toString();
					Account account = xmppConnectionService.findAccountByJid(accountJid);
					Contact contact = account.getRoster().getContact(contactJid);
					if (contact.showInRoster()) {
						jid.setError(getString(R.string.contact_already_exists));
					} else {
						xmppConnectionService.createContact(contact);
						switchToConversation(contact);
						dialog.dismiss();
					}
				} else {
					jid.setError(getString(R.string.invalid_jid));
				}
			}
		});
		
	}
	
	protected void switchToConversation(Contact contact) {
		Conversation conversation = xmppConnectionService.findOrCreateConversation(contact.getAccount(), contact.getJid(), false);
		switchToConversation(conversation, null, false);
	}
	
	private void populateAccountSpinner(Spinner spinner) {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mActivatedAccounts);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.start_conversation, menu);
		MenuItem menuCreateContact = (MenuItem) menu
				.findItem(R.id.action_create_contact);
		MenuItem menuCreateConference = (MenuItem) menu
				.findItem(R.id.action_create_conference);
		MenuItem menuSearch = (MenuItem) menu.findItem(R.id.action_search);
		if (getActionBar().getSelectedNavigationIndex() == 0) {
			menuCreateConference.setVisible(false);
		} else {
			menuCreateContact.setVisible(false);
		}
		mSearchView = (SearchView) menuSearch.getActionView();
		int id = mSearchView.getContext().getResources()
				.getIdentifier("android:id/search_src_text", null, null);
		TextView textView = (TextView) mSearchView.findViewById(id);
		textView.setTextColor(Color.WHITE);
		mSearchView.setOnQueryTextListener(this.mOnQueryTextListener);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_create_contact:
			showCreateContactDialog();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	void onBackendConnected() {
		filterContacts(null);
		this.mActivatedAccounts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.STATUS_DISABLED) {
				this.mActivatedAccounts.add(account.getJid());
			}
		}
		this.mKnownHosts = xmppConnectionService.getKnownHosts();
	}

	protected void filterContacts(String needle) {
		this.contacts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.STATUS_DISABLED) {
				for (Contact contact : account.getRoster().getContacts()) {
					if (contact.showInRoster() && contact.match(needle)) {
						this.contacts.add(contact);
					}
				}
			}
		}
		Collections.sort(this.contacts);
		mContactsAdapter.notifyDataSetChanged();
	}

	private void onTabChanged() {
		invalidateOptionsMenu();
	}

	private class ListItemAdapter extends ArrayAdapter<ListItem> {

		public ListItemAdapter(List<ListItem> objects) {
			super(getApplicationContext(), 0, objects);
		}

		@Override
		public View getView(int position, View view, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			ListItem item = getItem(position);
			if (view == null) {
				view = (View) inflater.inflate(R.layout.contact, null);
			}
			TextView name = (TextView) view
					.findViewById(R.id.contact_display_name);
			TextView jid = (TextView) view.findViewById(R.id.contact_jid);
			ImageView picture = (ImageView) view
					.findViewById(R.id.contact_photo);

			jid.setText(item.getJid());
			name.setText(item.getDisplayName());
			picture.setImageBitmap(UIHelper.getContactPicture(item, 48,
					this.getContext(), false));
			return view;
		}

	}

	public static class MyListFragment extends ListFragment {
		private AdapterView.OnItemClickListener mOnItemClickListener;
		private int mContextPosition = -1;

		@Override
		public void onListItemClick(ListView l, View v, int position, long id) {
			if (mOnItemClickListener != null) {
				mOnItemClickListener.onItemClick(l, v, position, id);
			}
		}

		public void setOnListItemClickListener(AdapterView.OnItemClickListener l) {
			this.mOnItemClickListener = l;
		}

		@Override
		public void onViewCreated(View view, Bundle savedInstanceState) {
			super.onViewCreated(view, savedInstanceState);
			registerForContextMenu(getListView());
		}

		@Override
		public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenuInfo menuInfo) {
			super.onCreateContextMenu(menu, v, menuInfo);
			getActivity().getMenuInflater().inflate(R.menu.contact_context,
					menu);
			AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
			this.mContextPosition = acmi.position;
		}

		@Override
		public boolean onContextItemSelected(MenuItem item) {
			StartConversation activity = (StartConversation) getActivity();
			switch(item.getItemId()) {
			case R.id.context_start_conversation:
				activity.openConversationForContact(mContextPosition);
				break;
			case R.id.context_contact_details:
				activity.openDetailsForContact(mContextPosition);
				break;
			case R.id.context_delete_contact:
				activity.deleteContact(mContextPosition);
				break;
			}
			return true;
		}
	}
}
