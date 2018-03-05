package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.MucOptions;
import rocks.xmpp.addr.Jid;

public class ChooseContactActivity extends AbstractSearchableListItemActivity {
	private List<String> mActivatedAccounts = new ArrayList<>();
	private List<String> mKnownHosts;

	private Set<Contact> selected;
	private Set<String> filterContacts;
	public static final String EXTRA_TITLE_RES_ID = "extra_title_res_id";

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		filterContacts = new HashSet<>();
		String[] contacts = getIntent().getStringArrayExtra("filter_contacts");
		if (contacts != null) {
			Collections.addAll(filterContacts, contacts);
		}

		if (getIntent().getBooleanExtra("multiple", false)) {
			getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
			getListView().setMultiChoiceModeListener(new MultiChoiceModeListener() {

				@Override
				public  boolean onPrepareActionMode(ActionMode mode, Menu menu) {
					return false;
				}

				@Override
				public boolean onCreateActionMode(ActionMode mode, Menu menu) {
					final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(),
							InputMethodManager.HIDE_IMPLICIT_ONLY);
					MenuInflater inflater = getMenuInflater();
					inflater.inflate(R.menu.select_multiple, menu);
					selected = new HashSet<>();
					return true;
				}

				@Override
				public void onDestroyActionMode(ActionMode mode) {
				}

				@Override
				public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
					switch(item.getItemId()) {
						case R.id.selection_submit:
							final Intent request = getIntent();
							final Intent data = new Intent();
							data.putExtra("conversation",
									request.getStringExtra("conversation"));
							String[] selection = getSelectedContactJids();
							data.putExtra("contacts", selection);
							data.putExtra("multiple", true);
							data.putExtra(EXTRA_ACCOUNT,request.getStringExtra(EXTRA_ACCOUNT));
							data.putExtra("subject", request.getStringExtra("subject"));
							setResult(RESULT_OK, data);
							finish();
							return true;
					}
					return false;
				}

				@Override
				public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
					Contact item = (Contact) getListItems().get(position);
					if (checked) {
						selected.add(item);
					} else {
						selected.remove(item);
					}
					int numSelected = selected.size();
					MenuItem selectButton = mode.getMenu().findItem(R.id.selection_submit);
					String buttonText = getResources().getQuantityString(R.plurals.select_contact,
							numSelected, numSelected);
					selectButton.setTitle(buttonText);
				}
			});
		}

		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(final AdapterView<?> parent, final View view,
					final int position, final long id) {
				final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(),
						InputMethodManager.HIDE_IMPLICIT_ONLY);
				final Intent request = getIntent();
				final Intent data = new Intent();
				final ListItem mListItem = getListItems().get(position);
				data.putExtra("contact", mListItem.getJid().toString());
				String account = request.getStringExtra(EXTRA_ACCOUNT);
				if (account == null && mListItem instanceof Contact) {
					account = ((Contact) mListItem).getAccount().getJid().asBareJid().toString();
				}
				data.putExtra(EXTRA_ACCOUNT, account);
				data.putExtra("conversation",
						request.getStringExtra("conversation"));
				data.putExtra("multiple", false);
				data.putExtra("subject", request.getStringExtra("subject"));
				setResult(RESULT_OK, data);
				finish();
			}
		});

	}

	@Override
	public void onStart() {
		super.onStart();
		Intent intent = getIntent();
		@StringRes
		int res = intent != null ? intent.getIntExtra(EXTRA_TITLE_RES_ID,R.string.title_activity_choose_contact) : R.string.title_activity_choose_contact;
		ActionBar bar = getSupportActionBar();
		if (bar != null) {
			try {
				bar.setTitle(res);
			} catch (Exception e) {
				bar.setTitle(R.string.title_activity_choose_contact);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		final Intent i = getIntent();
		boolean showEnterJid = i != null && i.getBooleanExtra("show_enter_jid", false);
		menu.findItem(R.id.action_create_contact).setVisible(showEnterJid);
		return true;
	}

	protected void filterContacts(final String needle) {
		getListItems().clear();
		if (xmppConnectionService == null) {
			getListItemAdapter().notifyDataSetChanged();
			return;
		}
		for (final Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				for (final Contact contact : account.getRoster().getContacts()) {
					if (contact.showInRoster() &&
							!filterContacts.contains(contact.getJid().asBareJid().toString())
							&& contact.match(this, needle)) {
						getListItems().add(contact);
					}
				}
			}
		}
		Collections.sort(getListItems());
		getListItemAdapter().notifyDataSetChanged();
	}

	private String[] getSelectedContactJids() {
		List<String> result = new ArrayList<>();
		for (Contact contact : selected) {
			result.add(contact.getJid().toString());
		}
		return result.toArray(new String[result.size()]);
	}


	public void refreshUiReal() {
		//nothing to do. This Activity doesn't implement any listeners
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_create_contact:
				showEnterJidDialog();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void showEnterJidDialog() {
		EnterJidDialog dialog = new EnterJidDialog(
			this, mKnownHosts, mActivatedAccounts,
			getString(R.string.enter_contact), getString(R.string.select),
			null, getIntent().getStringExtra(EXTRA_ACCOUNT), true
		);

		dialog.setOnEnterJidDialogPositiveListener(new EnterJidDialog.OnEnterJidDialogPositiveListener() {
			@Override
			public boolean onEnterJidDialogPositive(Jid accountJid, Jid contactJid) throws EnterJidDialog.JidError {
				final Intent request = getIntent();
				final Intent data = new Intent();
				data.putExtra("contact", contactJid.toString());
				data.putExtra(EXTRA_ACCOUNT, accountJid.toString());
				data.putExtra("conversation",
						request.getStringExtra("conversation"));
				data.putExtra("multiple", false);
				data.putExtra("subject", request.getStringExtra("subject"));
				setResult(RESULT_OK, data);
				finish();

				return true;
			}
		});

		dialog.show();
	}

	@Override
	void onBackendConnected() {
		filterContacts();
		this.mActivatedAccounts.clear();
		for (Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				if (Config.DOMAIN_LOCK != null) {
					this.mActivatedAccounts.add(account.getJid().getLocal());
				} else {
					this.mActivatedAccounts.add(account.getJid().asBareJid().toString());
				}
			}
		}
		this.mKnownHosts = xmppConnectionService.getKnownHosts();
	}

	public static Intent create(Activity activity, Conversation conversation) {
		final Intent intent = new Intent(activity, ChooseContactActivity.class);
		List<String> contacts = new ArrayList<>();
		if (conversation.getMode() == Conversation.MODE_MULTI) {
			for (MucOptions.User user : conversation.getMucOptions().getUsers(false)) {
				Jid jid = user.getRealJid();
				if (jid != null) {
					contacts.add(jid.asBareJid().toString());
				}
			}
		} else {
			contacts.add(conversation.getJid().asBareJid().toString());
		}
		intent.putExtra("filter_contacts", contacts.toArray(new String[contacts.size()]));
		intent.putExtra("conversation", conversation.getUuid());
		intent.putExtra("multiple", true);
		intent.putExtra("show_enter_jid", true);
		intent.putExtra(EXTRA_ACCOUNT, conversation.getAccount().getJid().asBareJid().toString());
		return intent;
	}
}
