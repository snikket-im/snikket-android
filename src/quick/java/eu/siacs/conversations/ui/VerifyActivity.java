package eu.siacs.conversations.ui;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.View;

import java.util.ArrayList;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityVerifyBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.util.PinEntryWrapper;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class VerifyActivity extends XmppActivity implements ClipboardManager.OnPrimaryClipChangedListener {

    private ActivityVerifyBinding binding;
    private Account account;
    private PinEntryWrapper pinEntryWrapper;
    private ClipboardManager clipboardManager;
    private String pasted = null;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pin = savedInstanceState != null ? savedInstanceState.getString("pin") : null;
        this.pasted = savedInstanceState != null ? savedInstanceState.getString("pasted") : null;
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_verify);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.pinEntryWrapper = new PinEntryWrapper(binding.pinBox);
        if (pin != null) {
            this.pinEntryWrapper.setPin(pin);
        }
        binding.back.setOnClickListener(this::onBackButton);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    }

    private void onBackButton(View view) {
        if (this.account != null) {
            xmppConnectionService.deleteAccount(account);
            Intent intent = new Intent(this, EnterPhoneNumberActivity.class);
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

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("pin", this.pinEntryWrapper.getPin());
        if (this.pasted != null) {
            savedInstanceState.putString("pasted", this.pasted);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        clipboardManager.addPrimaryClipChangedListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        clipboardManager.removePrimaryClipChangedListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pinEntryWrapper.isEmpty()) {
            pastePinFromClipboard();
        }
    }

    private void pastePinFromClipboard() {
        final ClipDescription description = clipboardManager != null ? clipboardManager.getPrimaryClipDescription() : null;
        if (description != null && description.hasMimeType(MIMETYPE_TEXT_PLAIN)) {
            final ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                final CharSequence clip = primaryClip.getItemAt(0).getText();
                if (PinEntryWrapper.isPin(clip) && !clip.toString().equals(this.pasted)) {
                    this.pasted = clip.toString();
                    pinEntryWrapper.setPin(clip.toString());
                    final Snackbar snackbar = Snackbar.make(binding.coordinator, R.string.possible_pin, Snackbar.LENGTH_LONG);
                    snackbar.setAction(R.string.undo, v -> pinEntryWrapper.clear());
                    snackbar.show();
                }
            }
        }
    }

    @Override
    public void onPrimaryClipChanged() {
        this.pasted = null;
        if (pinEntryWrapper.isEmpty()) {
            pastePinFromClipboard();
        }
    }
}
