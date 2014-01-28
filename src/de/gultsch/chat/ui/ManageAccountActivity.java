package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.ui.EditAccount.EditAccountListener;
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
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		setContentView(R.layout.manage_accounts);
		
		accountListView = (ListView) findViewById(R.id.account_list);
		accountListViewAdapter = new ArrayAdapter<Account>(getApplicationContext(), R.layout.account_row, this.accountList) {
			@Override
			public View getView(int position, View view, ViewGroup parent) {
				if (view == null) {
					LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					view = (View) inflater.inflate(R.layout.account_row, null);
				}
					((TextView) view.findViewById(R.id.account_jid)).setText(getItem(position).getJid());
				
				return view;
			}
		};
		accountListView.setAdapter(this.accountListViewAdapter);
		accountListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view, int position,
					long arg3) {
				EditAccount dialog = new EditAccount();
				dialog.setAccount(accountList.get(position));
				dialog.setEditAccountListener(new EditAccountListener() {
					
					@Override
					public void onAccountEdited(Account account) {
						xmppConnectionService.updateAccount(account);
					}

					@Override
					public void onAccountDelete(Account account) {
						
						Log.d("gultsch","deleting account:"+account.getJid());
						
						xmppConnectionService.deleteAccount(account);
						
						//dont bother finding the right account in the frontend list. just reload
						accountList.clear();
						accountList.addAll(xmppConnectionService.getAccounts());
						
						accountListViewAdapter.notifyDataSetChanged();
						
					}
				});
				dialog.show(getFragmentManager(),"edit_account");
			}
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		if (xmppConnectionServiceBound) {
			Log.d("gultsch","already bound");
			this.accountList.clear();
			this.accountList.addAll(xmppConnectionService
					.getAccounts());
			accountListViewAdapter.notifyDataSetChanged();
		}
	}
	
	@Override
	void onBackendConnected() {
		Log.d("gultsch","called on backend connected");
		this.accountList.clear();
		this.accountList.addAll(xmppConnectionService.getAccounts());
		accountListViewAdapter.notifyDataSetChanged();
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
			EditAccount dialog = new EditAccount();
			dialog.setEditAccountListener(new EditAccountListener() {
				
				@Override
				public void onAccountEdited(Account account) {
					xmppConnectionService.createAccount(account);
					accountList.add(account);
					accountListViewAdapter.notifyDataSetChanged();
				}

				@Override
				public void onAccountDelete(Account account) {
					//this will never be called
				}
			});
			dialog.show(getFragmentManager(),"add_account");
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
