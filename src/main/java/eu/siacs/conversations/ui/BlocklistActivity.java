package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.jid.Jid;

public class BlocklistActivity extends AbstractSearchableListItemActivity implements OnUpdateBlocklist {
	private List<String> mKnownHosts = new ArrayList<>();

	private Account account = null;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(final AdapterView<?> parent,
					final View view,
					final int position,
					final long id) {
				BlockContactDialog.show(BlocklistActivity.this, (Contact) getListItems().get(position));
				return true;
			}
		});
	}

	@Override
	public void onBackendConnected() {
		for (final Account account : xmppConnectionService.getAccounts()) {
			if (account.getJid().toString().equals(getIntent().getStringExtra(EXTRA_ACCOUNT))) {
				this.account = account;
				break;
			}
		}
		filterContacts();
		this.mKnownHosts = xmppConnectionService.getKnownHosts();
	}

	@Override
	protected void filterContacts(final String needle) {
		getListItems().clear();
		if (account != null) {
			for (final Jid jid : account.getBlocklist()) {
				final Contact contact = account.getRoster().getContact(jid);
				if (contact.match(this, needle) && contact.isBlocked()) {
					getListItems().add(contact);
				}
			}
			Collections.sort(getListItems());
		}
		getListItemAdapter().notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.findItem(R.id.action_block_jid).setVisible(true);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_block_jid:
				showEnterJidDialog();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	protected void showEnterJidDialog() {
		EnterJidDialog dialog = new EnterJidDialog(
				this, mKnownHosts, null,
				getString(R.string.block_jabber_id), getString(R.string.block),
				null, account.getJid().toBareJid().toString(), true
		);

		dialog.setOnEnterJidDialogPositiveListener(new EnterJidDialog.OnEnterJidDialogPositiveListener() {
			@Override
			public boolean onEnterJidDialogPositive(Jid accountJid, Jid contactJid) throws EnterJidDialog.JidError {
				Contact contact = account.getRoster().getContact(contactJid);
                if (xmppConnectionService.sendBlockRequest(contact, false)) {
					Toast.makeText(BlocklistActivity.this,R.string.corresponding_conversations_closed,Toast.LENGTH_SHORT).show();
				}
				return true;
			}
		});

		dialog.show();
	}

	protected void refreshUiReal() {
		final Editable editable = getSearchEditText().getText();
		if (editable != null) {
			filterContacts(editable.toString());
		} else {
			filterContacts();
		}
	}

	@Override
	public void OnUpdateBlocklist(final OnUpdateBlocklist.Status status) {
		refreshUi();
	}
}
