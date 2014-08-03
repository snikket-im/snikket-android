package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.EditAccountDialog.EditAccountListener;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import eu.siacs.conversations.xmpp.XmppConnection;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class ManageAccountActivity extends XmppActivity {

	protected boolean isActionMode = false;
	protected ActionMode actionMode;
	protected Account selectedAccountForActionMode = null;
	protected ManageAccountActivity activity = this;

	protected boolean firstrun = true;

	protected List<Account> accountList = new ArrayList<Account>();
	protected ListView accountListView;
	protected AccountAdapter mAccountAdapter;
	protected OnAccountUpdate accountChanged = new OnAccountUpdate() {

		@Override
		public void onAccountUpdate() {
			accountList.clear();
			accountList.addAll(xmppConnectionService.getAccounts());
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					mAccountAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	protected ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			if (selectedAccountForActionMode
					.isOptionSet(Account.OPTION_DISABLED)) {
				menu.findItem(R.id.mgmt_account_enable).setVisible(true);
				menu.findItem(R.id.mgmt_account_disable).setVisible(false);
			} else {
				menu.findItem(R.id.mgmt_account_disable).setVisible(true);
				menu.findItem(R.id.mgmt_account_enable).setVisible(false);
			}
			return true;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			// TODO Auto-generated method stub

		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.manageaccounts_context, menu);
			return true;
		}

		@Override
		public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
			if (item.getItemId() == R.id.mgmt_account_edit) {
				editAccount(selectedAccountForActionMode);
			} else if (item.getItemId() == R.id.mgmt_account_disable) {
				selectedAccountForActionMode.setOption(Account.OPTION_DISABLED,
						true);
				xmppConnectionService
						.updateAccount(selectedAccountForActionMode);
				mode.finish();
			} else if (item.getItemId() == R.id.mgmt_account_enable) {
				selectedAccountForActionMode.setOption(Account.OPTION_DISABLED,
						false);
				xmppConnectionService
						.updateAccount(selectedAccountForActionMode);
				mode.finish();
			} else if (item.getItemId() == R.id.mgmt_account_publish_avatar) {
				startActivity(new Intent(getApplicationContext(), PublishProfilePictureActivity.class));
			} else if (item.getItemId() == R.id.mgmt_account_delete) {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setTitle(getString(R.string.mgmt_account_are_you_sure));
				builder.setIconAttribute(android.R.attr.alertDialogIcon);
				builder.setMessage(getString(R.string.mgmt_account_delete_confirm_text));
				builder.setPositiveButton(getString(R.string.delete),
						new OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								xmppConnectionService
										.deleteAccount(selectedAccountForActionMode);
								selectedAccountForActionMode = null;
								mode.finish();
							}
						});
				builder.setNegativeButton(getString(R.string.cancel), null);
				builder.create().show();
			} else if (item.getItemId() == R.id.mgmt_account_announce_pgp) {
				if (activity.hasPgp()) {
					mode.finish();
					announcePgp(selectedAccountForActionMode, null);
				} else {
					activity.showInstallPgpDialog();
				}
			} else if (item.getItemId() == R.id.mgmt_otr_key) {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setTitle("OTR Fingerprint");
				String fingerprintTxt = selectedAccountForActionMode
						.getOtrFingerprint(getApplicationContext());
				View view = (View) getLayoutInflater().inflate(
						R.layout.otr_fingerprint, null);
				if (fingerprintTxt != null) {
					TextView fingerprint = (TextView) view
							.findViewById(R.id.otr_fingerprint);
					TextView noFingerprintView = (TextView) view
							.findViewById(R.id.otr_no_fingerprint);
					fingerprint.setText(fingerprintTxt);
					fingerprint.setVisibility(View.VISIBLE);
					noFingerprintView.setVisibility(View.GONE);
				}
				builder.setView(view);
				builder.setPositiveButton(getString(R.string.done), null);
				builder.create().show();
			} else if (item.getItemId() == R.id.mgmt_account_info) {
				AlertDialog.Builder builder = new AlertDialog.Builder(activity);
				builder.setTitle(getString(R.string.account_info));
				if (selectedAccountForActionMode.getStatus() == Account.STATUS_ONLINE) {
					XmppConnection xmpp = selectedAccountForActionMode
							.getXmppConnection();
					long connectionAge = (SystemClock.elapsedRealtime() - xmpp.lastConnect) / 60000;
					long sessionAge = (SystemClock.elapsedRealtime() - xmpp.lastSessionStarted) / 60000;
					long connectionAgeHours = connectionAge / 60;
					long sessionAgeHours = sessionAge / 60;
					View view = (View) getLayoutInflater().inflate(
							R.layout.server_info, null);
					TextView connection = (TextView) view
							.findViewById(R.id.connection);
					TextView session = (TextView) view
							.findViewById(R.id.session);
					TextView pcks_sent = (TextView) view
							.findViewById(R.id.pcks_sent);
					TextView pcks_received = (TextView) view
							.findViewById(R.id.pcks_received);
					TextView carbon = (TextView) view.findViewById(R.id.carbon);
					TextView stream = (TextView) view.findViewById(R.id.stream);
					TextView roster = (TextView) view.findViewById(R.id.roster);
					TextView presences = (TextView) view
							.findViewById(R.id.number_presences);
					presences.setText(selectedAccountForActionMode
							.countPresences() + "");
					pcks_received.setText("" + xmpp.getReceivedStanzas());
					pcks_sent.setText("" + xmpp.getSentStanzas());
					if (connectionAgeHours >= 2) {
						connection.setText(connectionAgeHours + " "
								+ getString(R.string.hours));
					} else {
						connection.setText(connectionAge + " "
								+ getString(R.string.mins));
					}
					if (xmpp.hasFeatureStreamManagment()) {
						if (sessionAgeHours >= 2) {
							session.setText(sessionAgeHours + " "
									+ getString(R.string.hours));
						} else {
							session.setText(sessionAge + " "
									+ getString(R.string.mins));
						}
						stream.setText(getString(R.string.yes));
					} else {
						stream.setText(getString(R.string.no));
						session.setText(connection.getText());
					}
					if (xmpp.hasFeaturesCarbon()) {
						carbon.setText(getString(R.string.yes));
					} else {
						carbon.setText(getString(R.string.no));
					}
					if (xmpp.hasFeatureRosterManagment()) {
						roster.setText(getString(R.string.yes));
					} else {
						roster.setText(getString(R.string.no));
					}
					builder.setView(view);
				} else {
					builder.setMessage(getString(R.string.mgmt_account_account_offline));
				}
				builder.setPositiveButton(getString(R.string.hide), null);
				builder.create().show();
			}
			return true;
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.manage_accounts);

		accountListView = (ListView) findViewById(R.id.account_list);
		final XmppActivity activity = this;
		this.mAccountAdapter = new AccountAdapter(this, accountList);
		accountListView.setAdapter(this.mAccountAdapter);
		accountListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
					int position, long arg3) {
				if (!isActionMode) {
					Account account = accountList.get(position);
					if (account.getStatus() == Account.STATUS_OFFLINE) {
						activity.xmppConnectionService.reconnectAccount(
								accountList.get(position), true);
					} else if (account.getStatus() == Account.STATUS_ONLINE) {
						activity.startActivity(new Intent(activity
								.getApplicationContext(),
								StartConversationActivity.class));
					} else if (account.getStatus() != Account.STATUS_DISABLED) {
						editAccount(account);
					}
				} else {
					selectedAccountForActionMode = accountList.get(position);
					actionMode.invalidate();
				}
			}
		});
		accountListView
				.setOnItemLongClickListener(new OnItemLongClickListener() {

					@Override
					public boolean onItemLongClick(AdapterView<?> arg0,
							View view, int position, long arg3) {
						if (!isActionMode) {
							accountListView
									.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
							accountListView.setItemChecked(position, true);
							selectedAccountForActionMode = accountList
									.get(position);
							actionMode = activity
									.startActionMode(mActionModeCallback);
							return true;
						} else {
							return false;
						}
					}
				});
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnAccountListChangedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		xmppConnectionService.setOnAccountListChangedListener(accountChanged);
		this.accountList.clear();
		this.accountList.addAll(xmppConnectionService.getAccounts());
		mAccountAdapter.notifyDataSetChanged();
		if ((this.accountList.size() == 0) && (this.firstrun)) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
			addAccount();
			this.firstrun = false;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.manageaccounts, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_add_account:
			addAccount();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigateUp() {
		if (xmppConnectionService.getConversations().size() == 0) {
			Intent contactsIntent = new Intent(this,
					StartConversationActivity.class);
			contactsIntent.setFlags(
			// if activity exists in stack, pop the stack and go back to it
					Intent.FLAG_ACTIVITY_CLEAR_TOP |
					// otherwise, make a new task for it
							Intent.FLAG_ACTIVITY_NEW_TASK |
							// don't use the new activity animation; finish
							// animation runs instead
							Intent.FLAG_ACTIVITY_NO_ANIMATION);
			startActivity(contactsIntent);
			finish();
			return true;
		} else {
			return super.onNavigateUp();
		}
	}

	private void editAccount(Account account) {
		EditAccountDialog dialog = new EditAccountDialog();
		dialog.setAccount(account);
		dialog.setEditAccountListener(new EditAccountListener() {

			@Override
			public void onAccountEdited(Account account) {
				xmppConnectionService.updateAccount(account);
				if (actionMode != null) {
					actionMode.finish();
				}
			}
		});
		dialog.show(getFragmentManager(), "edit_account");
		dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);

	}

	protected void addAccount() {
		final Activity activity = this;
		EditAccountDialog dialog = new EditAccountDialog();
		dialog.setEditAccountListener(new EditAccountListener() {

			@Override
			public void onAccountEdited(Account account) {
				xmppConnectionService.createAccount(account);
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
				activity.getActionBar().setHomeButtonEnabled(true);
			}
		});
		dialog.show(getFragmentManager(), "add_account");
		dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
	}

	@Override
	public void onActionModeStarted(ActionMode mode) {
		super.onActionModeStarted(mode);
		this.isActionMode = true;
	}

	@Override
	public void onActionModeFinished(ActionMode mode) {
		super.onActionModeFinished(mode);
		this.isActionMode = false;
		accountListView.clearChoices();
		accountListView.requestLayout();
		accountListView.post(new Runnable() {
			@Override
			public void run() {
				accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
			}
		});
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_ANNOUNCE_PGP) {
				announcePgp(selectedAccountForActionMode, null);
			}
		}
	}
}
