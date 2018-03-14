package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collection;
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
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

public class ChooseContactActivity extends AbstractSearchableListItemActivity {
	public static final String EXTRA_TITLE_RES_ID = "extra_title_res_id";
	private List<String> mActivatedAccounts = new ArrayList<>();
	private Collection<String> mKnownHosts;
	private Set<Contact> selected;
	private Set<String> filterContacts;

	private PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();

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
				public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
					return false;
				}

				@Override
				public boolean onCreateActionMode(ActionMode mode, Menu menu) {
					final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
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
					switch (item.getItemId()) {
						case R.id.selection_submit:
							final Intent request = getIntent();
							final Intent data = new Intent();
							data.putExtra("conversation",
									request.getStringExtra("conversation"));
							String[] selection = getSelectedContactJids();
							data.putExtra("contacts", selection);
							data.putExtra("multiple", true);
							data.putExtra(EXTRA_ACCOUNT, request.getStringExtra(EXTRA_ACCOUNT));
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

		getListView().setOnItemClickListener((parent, view, position, id) -> {
			final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
			final Intent request = getIntent();
			final Intent data = new Intent();
			final ListItem mListItem = getListItems().get(position);
			data.putExtra("contact", mListItem.getJid().toString());
			String account = request.getStringExtra(EXTRA_ACCOUNT);
			if (account == null && mListItem instanceof Contact) {
				account = ((Contact) mListItem).getAccount().getJid().asBareJid().toString();
			}
			data.putExtra(EXTRA_ACCOUNT, account);
			data.putExtra("conversation", request.getStringExtra("conversation"));
			data.putExtra("multiple", false);
			data.putExtra("subject", request.getStringExtra("subject"));
			setResult(RESULT_OK, data);
			finish();
		});
		final Intent i = getIntent();
		boolean showEnterJid = i != null && i.getBooleanExtra("show_enter_jid", false);
		if (showEnterJid) {
			this.binding.fab.setOnClickListener((v) -> showEnterJidDialog(null));
		} else {
			this.binding.fab.setVisibility(View.GONE);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Intent intent = getIntent();
		@StringRes
		int res = intent != null ? intent.getIntExtra(EXTRA_TITLE_RES_ID, R.string.title_activity_choose_contact) : R.string.title_activity_choose_contact;
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
		menu.findItem(R.id.action_scan_qr_code).setVisible(isCameraFeatureAvailable() && showEnterJid);
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
			case R.id.action_scan_qr_code:
				ScanActivity.scan(this);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void showEnterJidDialog(XmppUri uri) {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		Jid jid = uri == null ? null : uri.getJid();
		EnterJidDialog dialog = EnterJidDialog.newInstance(
				mKnownHosts,
				mActivatedAccounts,
				getString(R.string.enter_contact),
				getString(R.string.select),
				jid == null ? null : jid.asBareJid().toString(),
				getIntent().getStringExtra(EXTRA_ACCOUNT),
				true
		);

		dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
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
		});

		dialog.show(ft, "dialog");
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, requestCode, intent);
		ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, intent);
		if (xmppConnectionService != null) {
			handleActivityResult(activityResult);
		} else {
			this.postponedActivityResult.push(activityResult);
		}
	}

	private void handleActivityResult(ActivityResult activityResult) {
		if (activityResult.resultCode == RESULT_OK && activityResult.requestCode == ScanActivity.REQUEST_SCAN_QR_CODE) {
			String result = activityResult.data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
			XmppUri uri = new XmppUri(result == null ? "" : result);
			if (uri.isJidValid()) {
				showEnterJidDialog(uri);
			}
		}
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
		ActivityResult activityResult = this.postponedActivityResult.pop();
		if (activityResult != null) {
			handleActivityResult(activityResult);
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
		ScanActivity.onRequestPermissionResult(this, requestCode, grantResults);
	}
}
