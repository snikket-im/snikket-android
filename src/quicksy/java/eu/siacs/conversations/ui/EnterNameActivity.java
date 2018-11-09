package eu.siacs.conversations.ui;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNameBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.AccountUtils;

public class EnterNameActivity extends XmppActivity {

    private ActivityEnterNameBinding binding;

    private Account account;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_name);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.next.setOnClickListener(this::next);
    }

    private void next(View view) {
        if (account != null) {

            String name = this.binding.name.getText().toString().trim();

            account.setDisplayName(name);

            xmppConnectionService.publishDisplayName(account);

            Intent intent = new Intent(this, PublishProfilePictureActivity.class);
            intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
            intent.putExtra("setup", true);
            startActivity(intent);
        }
        finish();
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        this.account = AccountUtils.getFirst(xmppConnectionService);
    }
}
