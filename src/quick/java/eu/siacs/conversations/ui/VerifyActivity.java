package eu.siacs.conversations.ui;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.View;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityVerifyBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.util.PinEntryWrapper;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;

public class VerifyActivity extends XmppActivity {

    private ActivityVerifyBinding binding;
    private Account account;
    private PinEntryWrapper pinEntryWrapper;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_verify);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.pinEntryWrapper = new PinEntryWrapper(binding.pinBox);
        binding.back.setOnClickListener(this::onBackButton);
    }

    private void onBackButton(View view) {
        if (this.account != null) {
            xmppConnectionService.deleteAccount(account);
            Intent intent = new Intent(this,EnterPhoneNumberActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        this.account = AccountUtils.getFirst(xmppConnectionService);
        if (this.account == null) {
            return;
        }
        this.binding.weHaveSent.setText(Html.fromHtml(getString(R.string.we_have_sent_you_an_sms, PhoneNumberUtilWrapper.prettyPhoneNumber(this, this.account.getJid()))));
    }
}
