package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import rocks.xmpp.addr.Jid;

public class ShareViaAccountActivity extends XmppActivity {
    public static final String EXTRA_CONTACT = "contact";
    public static final String EXTRA_BODY = "body";

    protected final List<Account> accountList = new ArrayList<>();
    protected ListView accountListView;
    protected AccountAdapter mAccountAdapter;

    @Override
    protected void refreshUiReal() {
        synchronized (this.accountList) {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
        }
        mAccountAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_manage_accounts);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        accountListView = findViewById(R.id.account_list);
        this.mAccountAdapter = new AccountAdapter(this, accountList, false);
        accountListView.setAdapter(this.mAccountAdapter);
        accountListView.setOnItemClickListener((arg0, view, position, arg3) -> {
            final Account account = accountList.get(position);
            final String body = getIntent().getStringExtra(EXTRA_BODY);

            try {
                final Jid contact = Jid.of(getIntent().getStringExtra(EXTRA_CONTACT));
                final Conversation conversation = xmppConnectionService.findOrCreateConversation(
                        account, contact, false, false);
                switchToConversation(conversation, body);
            } catch (IllegalArgumentException e) {
                // ignore error
            }

            finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    @Override
    void onBackendConnected() {
        final int numAccounts = xmppConnectionService.getAccounts().size();

        if (numAccounts == 1) {
            final String body = getIntent().getStringExtra(EXTRA_BODY);
            final Account account = xmppConnectionService.getAccounts().get(0);

            try {
                final Jid contact = Jid.of(getIntent().getStringExtra(EXTRA_CONTACT));
                final Conversation conversation = xmppConnectionService.findOrCreateConversation(
                        account, contact, false, false);
                switchToConversation(conversation, body);
            } catch (IllegalArgumentException e) {
                // ignore error
            }

            finish();
        } else {
            refreshUiReal();
        }
    }
}
