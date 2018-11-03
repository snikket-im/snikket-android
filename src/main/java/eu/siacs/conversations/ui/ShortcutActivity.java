package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.util.Collections;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;

public class ShortcutActivity extends AbstractSearchableListItemActivity{

    @Override
    protected void refreshUiReal() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getListView().setOnItemClickListener((parent, view, position, id) -> {
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getSearchEditText().getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);

            ListItem listItem = getListItems().get(position);
            Intent shortcut = xmppConnectionService.getShortcutService().createShortcut(((Contact) listItem));
            setResult(RESULT_OK,shortcut);
            finish();
        });
        binding.fab.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ActionBar bar = getSupportActionBar();
        if(bar != null){
            bar.setTitle(R.string.create_shortcut);
        }
    }

    @Override
    protected void filterContacts(String needle) {
        getListItems().clear();
        if (xmppConnectionService == null) {
            getListItemAdapter().notifyDataSetChanged();
            return;
        }
        for (final Account account : xmppConnectionService.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                for (final Contact contact : account.getRoster().getContacts()) {
                    if (contact.showInContactList()
                            && contact.match(this, needle)) {
                        getListItems().add(contact);
                    }
                }
            }
        }
        Collections.sort(getListItems());
        getListItemAdapter().notifyDataSetChanged();
    }
}
