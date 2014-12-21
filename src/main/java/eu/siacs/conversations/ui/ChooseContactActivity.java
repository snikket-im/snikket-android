package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;

import java.util.Collections;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;

public class ChooseContactActivity extends AbstractSearchableListItemActivity {
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
				String account = request.getStringExtra("account");
				if (account == null && mListItem instanceof Contact) {
					account = ((Contact) mListItem).getAccount().getJid().toBareJid().toString();
				}
				data.putExtra("account", account);
				data.putExtra("conversation",
						request.getStringExtra("conversation"));
				setResult(RESULT_OK, data);
				finish();
			}
		});

	}

	protected void filterContacts(final String needle) {
		getListItems().clear();
		for (final Account account : xmppConnectionService.getAccounts()) {
			if (account.getStatus() != Account.State.DISABLED) {
				for (final Contact contact : account.getRoster().getContacts()) {
					if (contact.showInRoster() && contact.match(needle)) {
						getListItems().add(contact);
					}
				}
			}
		}
		Collections.sort(getListItems());
		getListItemAdapter().notifyDataSetChanged();
	}
}
