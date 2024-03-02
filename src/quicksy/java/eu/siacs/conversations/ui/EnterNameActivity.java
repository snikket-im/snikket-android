package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNameBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.AbstractQuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.AccountUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public class EnterNameActivity extends XmppActivity
        implements XmppConnectionService.OnAccountUpdate {

    private ActivityEnterNameBinding binding;

    private Account account;

    private final AtomicBoolean setNick = new AtomicBoolean(false);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_name);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.next.setOnClickListener(this::next);
        this.setNick.set(
                savedInstanceState != null && savedInstanceState.getBoolean("set_nick", false));
    }

    private void next(final View view) {
        if (account == null) {
            return;
        }
        final String name = this.binding.name.getText().toString().trim();
        account.setDisplayName(name);
        xmppConnectionService.publishDisplayName(account);
        final Intent intent;
        if (AbstractQuickConversationsService.isQuicksyPlayStore()) {
            intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            intent.putExtra("init", true);
            intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        } else {
            intent = new Intent(this, PublishProfilePictureActivity.class);
            intent.putExtra("setup", true);
        }
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        startActivity(intent);
        finish();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean("set_nick", this.setNick.get());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void refreshUiReal() {
        checkSuggestPreviousNick();
    }

    @Override
    void onBackendConnected() {
        this.account = AccountUtils.getFirst(xmppConnectionService);
        checkSuggestPreviousNick();
    }

    private void checkSuggestPreviousNick() {
        final String displayName = this.account == null ? null : this.account.getDisplayName();
        if (displayName != null) {
            if (setNick.compareAndSet(false, true) && this.binding.name.getText().length() == 0) {
                this.binding.name.getText().append(displayName);
            }
        }
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }
}
