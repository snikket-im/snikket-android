package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.ui.EditAccount.EditAccountListener;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ManageAccountActivity extends XmppActivity {

	protected List<Account> accountList = new ArrayList<Account>();
	protected ListView accountListView;
	protected ArrayAdapter<Account> accountListViewAdapter;
	protected OnAccountListChangedListener accountChanged = new OnAccountListChangedListener() {

		@Override
		public void onAccountListChangedListener() {
			Log.d("xmppService", "ui on account list changed listener");
			accountList.clear();
			accountList.addAll(xmppConnectionService.getAccounts());
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					if (accountList.size() == 1) {
						startActivity(new Intent(getApplicationContext(),
								NewConversationActivity.class));
					}
					accountListViewAdapter.notifyDataSetChanged();
				}
			});
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.manage_accounts);

		accountListView = (ListView) findViewById(R.id.account_list);
		accountListViewAdapter = new ArrayAdapter<Account>(
				getApplicationContext(), R.layout.account_row, this.accountList) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				Account account = getItem(position);
				if (view == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(R.layout.account_row, null);
				}
				((TextView) view.findViewById(R.id.account_jid))
						.setText(account.getJid());
				TextView statusView = (TextView) view
						.findViewById(R.id.account_status);
				switch (account.getStatus()) {
				case Account.STATUS_ONLINE:
					statusView.setText("online");
					statusView.setTextColor(0xFF83b600);
					break;
				case Account.STATUS_OFFLINE:
					statusView.setText("offline");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_UNAUTHORIZED:
					statusView.setText("unauthorized");
					statusView.setTextColor(0xFFe92727);
					break;
				case Account.STATUS_SERVER_NOT_FOUND:
					statusView.setText("server not found");
					statusView.setTextColor(0xFFe92727);
					break;
				default:
					break;
				}

				return view;
			}
		};
		accountListView.setAdapter(this.accountListViewAdapter);
		accountListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
					int position, long arg3) {
				EditAccount dialog = new EditAccount();
				dialog.setAccount(accountList.get(position));
				dialog.setEditAccountListener(new EditAccountListener() {

					@Override
					public void onAccountEdited(Account account) {
						xmppConnectionService.updateAccount(account);
					}

					@Override
					public void onAccountDelete(Account account) {
						xmppConnectionService.deleteAccount(account);
					}
				});
				dialog.show(getFragmentManager(), "edit_account");
			}
		});
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnAccountListChangedListener();
			unbindService(mConnection);
			xmppConnectionServiceBound = false;
		}
	}

	@Override
	void onBackendConnected() {
		xmppConnectionService.setOnAccountListChangedListener(accountChanged);
		this.accountList.clear();
		this.accountList.addAll(xmppConnectionService.getAccounts());
		accountListViewAdapter.notifyDataSetChanged();
		if (this.accountList.size() == 0) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			addAccount();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.manageaccounts, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startActivity(new Intent(this, SettingsActivity.class));
			break;
		case R.id.action_add_account:
			addAccount();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void addAccount() {
		final Activity activity = this;
		EditAccount dialog = new EditAccount();
		dialog.setEditAccountListener(new EditAccountListener() {

			@Override
			public void onAccountEdited(Account account) {
				xmppConnectionService.createAccount(account);
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);
			}

			@Override
			public void onAccountDelete(Account account) {
				// this will never be called
			}
		});
		dialog.show(getFragmentManager(), "add_account");
	}
}
