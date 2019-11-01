package eu.siacs.conversations.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import rocks.xmpp.addr.Jid;

public class ChooseAccountForProfilePictureActivity extends XmppActivity {

    protected final List<Account> accountList = new ArrayList<>();
    protected ListView accountListView;
    protected AccountAdapter mAccountAdapter;

    @Override
    protected void refreshUiReal() {
        loadEnabledAccounts();
        mAccountAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar(), false);
        accountListView = findViewById(R.id.account_list);
        this.mAccountAdapter = new AccountAdapter(this, accountList, false);
        accountListView.setAdapter(this.mAccountAdapter);
        accountListView.setOnItemClickListener((arg0, view, position, arg3) -> {
            final Account account = accountList.get(position);
            goToProfilePictureActivity(account);
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
        loadEnabledAccounts();
        if (accountList.size() == 1) {
            goToProfilePictureActivity(accountList.get(0));
            return;
        }
        mAccountAdapter.notifyDataSetChanged();
    }

    private void loadEnabledAccounts() {
        accountList.clear();
        for(Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                accountList.add(account);
            }
        }
    }

    private void goToProfilePictureActivity(Account account) {
        final Intent startIntent = getIntent();
        final Uri uri = startIntent == null ? null : startIntent.getData();
        if (uri != null) {
            Intent intent = new Intent(this, PublishProfilePictureActivity.class);
            intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toString());
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (SecurityException e) {
                Toast.makeText(this, R.string.sharing_application_not_grant_permission, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        finish();
    }
}
