package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Editable;
import android.widget.Toast;

import java.util.Collections;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import rocks.xmpp.addr.Jid;

public class BlocklistActivity extends AbstractSearchableListItemActivity implements OnUpdateBlocklist {

	private Account account = null;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getListView().setOnItemLongClickListener((parent, view, position, id) -> {
			BlockContactDialog.show(BlocklistActivity.this, (Contact) getListItems().get(position));
			return true;
		});
		this.binding.fab.show();
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
		Fragment fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG);
		if (fragment instanceof OnBackendConnected) {
			((OnBackendConnected) fragment).onBackendConnected();
		}
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
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);
		EnterJidDialog dialog = EnterJidDialog.newInstance(
				null,
				getString(R.string.block_jabber_id),
				getString(R.string.block),
				null,
				account.getJid().asBareJid().toString(),
				true,
				false
		);

		dialog.setOnEnterJidDialogPositiveListener((accountJid, contactJid) -> {
			Contact contact = account.getRoster().getContact(contactJid);
			if (xmppConnectionService.sendBlockRequest(contact, false)) {
				Toast.makeText(BlocklistActivity.this, R.string.corresponding_conversations_closed, Toast.LENGTH_SHORT).show();
			}
			return true;
		});

		dialog.show(ft, "dialog");
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
