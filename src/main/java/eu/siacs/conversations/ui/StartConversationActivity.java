package eu.siacs.conversations.ui;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.ui.adapter.ListItemAdapter;
import eu.siacs.conversations.utils.Validator;

public class StartConversationActivity extends XmppActivity {

	private Tab mContactsTab;
	private Tab mConferencesTab;
	private ViewPager mViewPager;

	private MyListFragment mContactsListFragment = new MyListFragment();
	private List<ListItem> contacts = new ArrayList<ListItem>();
	private ArrayAdapter<ListItem> mContactsAdapter;

	private MyListFragment mConferenceListFragment = new MyListFragment();
	private List<ListItem> conferences = new ArrayList<ListItem>();
	private ArrayAdapter<ListItem> mConferenceAdapter;

	private List<String> mActivatedAccounts = new ArrayList<String>();
	private List<String> mKnownHosts;
	private List<String> mKnownConferenceHosts;

	private Menu mOptionsMenu;
	private EditText mSearchEditText;

	public int conference_context_id;
	public int contact_context_id;

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

	private MenuItem.OnActionExpandListener mOnActionExpandListener = new MenuItem.OnActionExpandListener() {

		@Override
		public boolean onMenuItemActionExpand(MenuItem item) {
			mSearchEditText.post(new Runnable() {

				@Override
				public void run() {
					mSearchEditText.requestFocus();
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.showSoftInput(mSearchEditText,
							InputMethodManager.SHOW_IMPLICIT);
				}
			});

			return true;
		}

		@Override
		public boolean onMenuItemActionCollapse(MenuItem item) {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(mSearchEditText.getWindowToken(),
					InputMethodManager.HIDE_IMPLICIT_ONLY);
			mSearchEditText.setText("");
			filter(null);
			return true;
		}
	};
	private TextWatcher mSearchTextWatcher = new TextWatcher() {

		@Override
		public void afterTextChanged(Editable editable) {
			filter(editable.toString());
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
		}
	};
	private OnRosterUpdate onRosterUpdate = new OnRosterUpdate() {

		@Override
		public void onRosterUpdate() {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (mSearchEditText != null) {
						filter(mSearchEditText.getText().toString());
					}
				}
			});
		}
	};
	private MenuItem mMenuSearchView;
	private String mInitialJid;

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

		mConferenceAdapter = new ListItemAdapter(this, conferences);
		mConferenceListFragment.setListAdapter(mConferenceAdapter);
		mConferenceListFragment.setContextMenu(R.menu.conference_context);
		mConferenceListFragment
				.setOnListItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
							int position, long arg3) {
						openConversationForBookmark(position);
					}
				});

		mContactsAdapter = new ListItemAdapter(this, contacts);
		mContactsListFragment.setListAdapter(mContactsAdapter);
		mContactsListFragment.setContextMenu(R.menu.contact_context);
		mContactsListFragment
				.setOnListItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
							int position, long arg3) {
						openConversationForContact(position);
					}
				});

	}

	@Override
	public void onStop() {
		super.onStop();
		xmppConnectionService.removeOnRosterUpdateListener();
	}

	protected void openConversationForContact(int position) {
		Contact contact = (Contact) contacts.get(position);
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(contact.getAccount(),
						contact.getJid(), false);
		switchToConversation(conversation);
	}

	protected void openConversationForContact() {
		int position = contact_context_id;
		openConversationForContact(position);
	}

	protected void openConversationForBookmark() {
		openConversationForBookmark(conference_context_id);
	}

	protected void openConversationForBookmark(int position) {
		Bookmark bookmark = (Bookmark) conferences.get(position);
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(bookmark.getAccount(),
						bookmark.getJid(), true);
		conversation.setBookmark(bookmark);
		if (!conversation.getMucOptions().online()) {
			xmppConnectionService.joinMuc(conversation);
		}
		if (!bookmark.autojoin()) {
			bookmark.setAutojoin(true);
			xmppConnectionService.pushBookmarks(bookmark.getAccount());
		}
		switchToConversation(conversation);
	}

	protected void openDetailsForContact() {
		int position = contact_context_id;
		Contact contact = (Contact) contacts.get(position);
		switchToContactDetails(contact);
	}

	protected void deleteContact() {
		int position = contact_context_id;
		final Contact contact = (Contact) contacts.get(position);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setTitle(R.string.action_delete_contact);
		builder.setMessage(getString(R.string.remove_contact_text,
				contact.getJid()));
		builder.setPositiveButton(R.string.delete, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				xmppConnectionService.deleteContactOnServer(contact);
				filter(mSearchEditText.getText().toString());
			}
		});
		builder.create().show();

	}

	protected void deleteConference() {
		int position = conference_context_id;
		final Bookmark bookmark = (Bookmark) conferences.get(position);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setTitle(R.string.delete_bookmark);
		builder.setMessage(getString(R.string.remove_bookmark_text,
				bookmark.getJid()));
		builder.setPositiveButton(R.string.delete, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				bookmark.unregisterConversation();
				Account account = bookmark.getAccount();
				account.getBookmarks().remove(bookmark);
				xmppConnectionService.pushBookmarks(account);
				filter(mSearchEditText.getText().toString());
			}
		});
		builder.create().show();

	}

	@SuppressLint("InflateParams")
	protected void showCreateContactDialog(String prefilledJid) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.create_contact);
		View dialogView = getLayoutInflater().inflate(
				R.layout.create_contact_dialog, null);
		final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
				.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(this,
				android.R.layout.simple_list_item_1, mKnownHosts));
		if (prefilledJid != null) {
			jid.append(prefilledJid);
		}
		populateAccountSpinner(spinner);
		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.create, null);
		final AlertDialog dialog = builder.create();
		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						if (!xmppConnectionServiceBound) {
							return;
						}
						if (Validator.isValidJid(jid.getText().toString())) {
							String accountJid = (String) spinner
									.getSelectedItem();
							String contactJid = jid.getText().toString();
							Account account = xmppConnectionService
									.findAccountByJid(accountJid);
							if (account == null) {
								dialog.dismiss();
								return;
							}
							Contact contact = account.getRoster().getContact(
									contactJid);
							if (contact.showInRoster()) {
								jid.setError(getString(R.string.contact_already_exists));
							} else {
								xmppConnectionService.createContact(contact);
								dialog.dismiss();
								switchToConversation(contact);
							}
						} else {
							jid.setError(getString(R.string.invalid_jid));
						}
					}
				});

	}

	@SuppressLint("InflateParams")
	protected void showJoinConferenceDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.join_conference);
		View dialogView = getLayoutInflater().inflate(
				R.layout.join_conference_dialog, null);
		final Spinner spinner = (Spinner) dialogView.findViewById(R.id.account);
		final AutoCompleteTextView jid = (AutoCompleteTextView) dialogView
				.findViewById(R.id.jid);
		jid.setAdapter(new KnownHostsAdapter(this,
				android.R.layout.simple_list_item_1, mKnownConferenceHosts));
		populateAccountSpinner(spinner);
		final CheckBox bookmarkCheckBox = (CheckBox) dialogView
				.findViewById(R.id.bookmark);
		builder.setView(dialogView);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.join, null);
		final AlertDialog dialog = builder.create();
		dialog.show();
		dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
				new View.OnClickListener() {

					@Override
					public void onClick(View v) {
						if (!xmppConnectionServiceBound) {
							return;
						}
						if (Validator.isValidJid(jid.getText().toString())) {
							String accountJid = (String) spinner
									.getSelectedItem();
							String conferenceJid = jid.getText().toString();
							Account account = xmppConnectionService
									.findAccountByJid(accountJid);
							if (account == null) {
								dialog.dismiss();
								return;
							}
							if (bookmarkCheckBox.isChecked()) {
								if (account.hasBookmarkFor(conferenceJid)) {
									jid.setError(getString(R.string.bookmark_already_exists));
								} else {
									Bookmark bookmark = new Bookmark(account,
											conferenceJid);
									bookmark.setAutojoin(true);
									account.getBookmarks().add(bookmark);
									xmppConnectionService
											.pushBookmarks(account);
									Conversation conversation = xmppConnectionService
											.findOrCreateConversation(account,
													conferenceJid, true);
									conversation.setBookmark(bookmark);
									if (!conversation.getMucOptions().online()) {
										xmppConnectionService
												.joinMuc(conversation);
									}
									dialog.dismiss();
									switchToConversation(conversation);
								}
							} else {
								Conversation conversation = xmppConnectionService
										.findOrCreateConversation(account,
												conferenceJid, true);
								if (!conversation.getMucOptions().online()) {
									xmppConnectionService.joinMuc(conversation);
								}
								dialog.dismiss();
								switchToConversation(conversation);
							}
						} else {
							jid.setError(getString(R.string.invalid_jid));
						}
					}
				});
	}

	protected void switchToConversation(Contact contact) {
		Conversation conversation = xmppConnectionService
				.findOrCreateConversation(contact.getAccount(),
						contact.getJid(), false);
		switchToConversation(conversation);
	}

	private void populateAccountSpinner(Spinner spinner) {
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, mActivatedAccounts);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		this.mOptionsMenu = menu;
		getMenuInflater().inflate(R.menu.start_conversation, menu);
		MenuItem menuCreateContact = menu
				.findItem(R.id.action_create_contact);
		MenuItem menuCreateConference = menu
				.findItem(R.id.action_join_conference);
		mMenuSearchView = menu.findItem(R.id.action_search);
		mMenuSearchView.setOnActionExpandListener(mOnActionExpandListener);
		View mSearchView = mMenuSearchView.getActionView();
		mSearchEditText = (EditText) mSearchView
				.findViewById(R.id.search_field);
		mSearchEditText.addTextChangedListener(mSearchTextWatcher);
		if (getActionBar().getSelectedNavigationIndex() == 0) {
			menuCreateConference.setVisible(false);
		} else {
			menuCreateContact.setVisible(false);
		}
		if (mInitialJid != null) {
			mMenuSearchView.expandActionView();
			mSearchEditText.append(mInitialJid);
			filter(mInitialJid);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_create_contact:
			showCreateContactDialog(null);
			return true;
		case R.id.action_join_conference:
			showJoinConferenceDialog();
			return true;
		case R.id.action_scan_qr_code:
			new IntentIntegrator(this).initiateScan();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_SEARCH && !event.isLongPress()) {
			mOptionsMenu.findItem(R.id.action_search).expandActionView();
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
			IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
			if (scanResult != null && scanResult.getFormatName() != null) {
				String data = scanResult.getContents();
				Log.d(Config.LOGTAG, data);
			}
		}
		super.onActivityResult(requestCode,requestCode,intent);
	}

	@Override
	protected void onBackendConnected() {
		xmppConnectionService.setOnRosterUpdateListener(this.onRosterUpdate);
		this.mActivatedAccounts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.STATUS_DISABLED) {
				this.mActivatedAccounts.add(account.getJid());
			}
		}
		this.mKnownHosts = xmppConnectionService.getKnownHosts();
		this.mKnownConferenceHosts = xmppConnectionService
				.getKnownConferenceHosts();
		if (!startByIntent()) {
			if (mSearchEditText != null) {
				filter(mSearchEditText.getText().toString());
			} else {
				filter(null);
			}
		}
	}

	protected boolean startByIntent() {
		if (getIntent() != null
				&& Intent.ACTION_SENDTO.equals(getIntent().getAction())) {
			try {
				String jid = URLDecoder.decode(
						getIntent().getData().getEncodedPath(), "UTF-8").split(
						"/")[1];
				setIntent(null);
				return handleJid(jid);
			} catch (UnsupportedEncodingException e) {
				setIntent(null);
				return false;
			}
		} else if (getIntent() != null
				&& Intent.ACTION_VIEW.equals(getIntent().getAction())) {
			Uri uri = getIntent().getData();
			String jid = uri.getSchemeSpecificPart().split("\\?")[0];
			return handleJid(jid);
		}
		return false;
	}

	private boolean handleJid(String jid) {
		List<Contact> contacts = xmppConnectionService.findContacts(jid);
		if (contacts.size() == 0) {
			showCreateContactDialog(jid);
			return false;
		} else if (contacts.size() == 1) {
			switchToConversation(contacts.get(0));
			return true;
		} else {
			if (mMenuSearchView != null) {
				mMenuSearchView.expandActionView();
				mSearchEditText.setText(jid);
				filter(jid);
			} else {
				mInitialJid = jid;
			}
			return true;
		}
	}

	protected void filter(String needle) {
		if (xmppConnectionServiceBound) {
			this.filterContacts(needle);
			this.filterConferences(needle);
		}
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

	protected void filterConferences(String needle) {
		this.conferences.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.STATUS_DISABLED) {
				for (Bookmark bookmark : account.getBookmarks()) {
					if (bookmark.match(needle)) {
						this.conferences.add(bookmark);
					}
				}
			}
		}
		Collections.sort(this.conferences);
		mConferenceAdapter.notifyDataSetChanged();
	}

	private void onTabChanged() {
		invalidateOptionsMenu();
	}

	public static class MyListFragment extends ListFragment {
		private AdapterView.OnItemClickListener mOnItemClickListener;
		private int mResContextMenu;

		public void setContextMenu(int res) {
			this.mResContextMenu = res;
		}

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
			getListView().setFastScrollEnabled(true);
		}

		@Override
		public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenuInfo menuInfo) {
			super.onCreateContextMenu(menu, v, menuInfo);
			StartConversationActivity activity = (StartConversationActivity) getActivity();
			activity.getMenuInflater().inflate(mResContextMenu, menu);
			AdapterView.AdapterContextMenuInfo acmi = (AdapterContextMenuInfo) menuInfo;
			if (mResContextMenu == R.menu.conference_context) {
				activity.conference_context_id = acmi.position;
			} else {
				activity.contact_context_id = acmi.position;
			}
		}

		@Override
		public boolean onContextItemSelected(MenuItem item) {
			StartConversationActivity activity = (StartConversationActivity) getActivity();
			switch (item.getItemId()) {
			case R.id.context_start_conversation:
				activity.openConversationForContact();
				break;
			case R.id.context_contact_details:
				activity.openDetailsForContact();
				break;
			case R.id.context_delete_contact:
				activity.deleteContact();
				break;
			case R.id.context_join_conference:
				activity.openConversationForBookmark();
				break;
			case R.id.context_delete_conference:
				activity.deleteConference();
			}
			return true;
		}
	}
}
