package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.text.Editable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import rocks.xmpp.addr.Jid;

public class BlocklistActivity extends AbstractSearchableListItemActivity implements OnUpdateBlocklist {
	private Collection<String> mKnownHosts = new ArrayList<>();

	private Account account = null;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getListView().setOnItemLongClickListener((parent, view, position, id) -> {
			BlockContactDialog.show(BlocklistActivity.this, (Contact) getListItems().get(position));
			return true;
		});
		this.binding.fab.setOnClickListener((v)->showEnterJidDialog());
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

	protected void showEnterJidDialog() {
		EnterJidDialog dialog = new EnterJidDialog(
				this, mKnownHosts, null,
				getString(R.string.block_jabber_id), getString(R.string.block),
				null, account.getJid().asBareJid().toString(), true
		);

		dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
			Contact contact = account.getRoster().getContact(contactJid);
			if (xmppConnectionService.sendBlockRequest(contact, false)) {
				Toast.makeText(BlocklistActivity.this, R.string.corresponding_conversations_closed, Toast.LENGTH_SHORT).show();
			}
			return true;
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
