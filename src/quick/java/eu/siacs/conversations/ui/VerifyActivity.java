package eu.siacs.conversations.ui;

import android.app.AlertDialog;
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

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityVerifyBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.util.PinEntryWrapper;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;

public class VerifyActivity extends XmppActivity implements ClipboardManager.OnPrimaryClipChangedListener, QuickConversationsService.OnVerification {

    private ActivityVerifyBinding binding;
    private Account account;
    private PinEntryWrapper pinEntryWrapper;
    private ClipboardManager clipboardManager;
    private String pasted = null;
    private boolean verifying = false;


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pin = savedInstanceState != null ? savedInstanceState.getString("pin") : null;
        boolean verifying = savedInstanceState != null && savedInstanceState.getBoolean("verifying");
        this.pasted = savedInstanceState != null ? savedInstanceState.getString("pasted") : null;
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_verify);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.pinEntryWrapper = new PinEntryWrapper(binding.pinBox);
        if (pin != null) {
            this.pinEntryWrapper.setPin(pin);
        }
        binding.back.setOnClickListener(this::onBackButton);
        binding.next.setOnClickListener(this::onNextButton);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        setVerifyingState(verifying);
    }

    private void onBackButton(View view) {
        if (this.verifying) {
            setVerifyingState(false);
            return;
        }
        final Intent intent = new Intent(this, EnterPhoneNumberActivity.class);
        if (this.account != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.abort_registration_procedure);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> {
                xmppConnectionService.deleteAccount(account);
                startActivity(intent);
                finish();
            });
            builder.setNegativeButton(R.string.no, null);
            builder.create().show();
        } else {
            startActivity(intent);
            finish();
        }
    }

    private void onNextButton(View view) {
        final String pin = pinEntryWrapper.getPin();
        if (PinEntryWrapper.isValidPin(pin)) {
            if (account != null && xmppConnectionService != null) {
                setVerifyingState(true);
                xmppConnectionService.getQuickConversationsService().verify(account, pin);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.please_enter_pin);
            builder.setPositiveButton(R.string.ok, null);
            builder.create().show();
        }
    }

    private void setVerifyingState(boolean verifying) {
        this.verifying = verifying;
        this.binding.back.setText(verifying ? R.string.cancel : R.string.back);
        this.binding.next.setEnabled(!verifying);
        this.binding.next.setText(verifying ? R.string.verifying : R.string.next);
        this.binding.resendSms.setVisibility(verifying ? View.GONE : View.VISIBLE);
        pinEntryWrapper.setEnabled(!verifying);
        this.binding.progressBar.setVisibility(verifying ? View.VISIBLE : View.GONE);
        this.binding.progressBar.setIndeterminate(verifying);
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.getQuickConversationsService().addOnVerificationListener(this);
        this.account = AccountUtils.getFirst(xmppConnectionService);
        if (this.account == null) {
            return;
        }
        this.binding.weHaveSent.setText(Html.fromHtml(getString(R.string.we_have_sent_you_an_sms, PhoneNumberUtilWrapper.prettyPhoneNumber(this, this.account.getJid()))));
        setVerifyingState(xmppConnectionService.getQuickConversationsService().isVerifying());
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("pin", this.pinEntryWrapper.getPin());
        savedInstanceState.putBoolean("verifying", this.verifying);
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
        if (xmppConnectionService != null) {
            xmppConnectionService.getQuickConversationsService().removeOnVerificationListener(this);
        }
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
                if (PinEntryWrapper.isValidPin(clip) && !clip.toString().equals(this.pasted)) {
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

    @Override
    public void onVerificationFailed() {
        runOnUiThread(() -> {
            setVerifyingState(false);
        });
    }

    @Override
    public void onVerificationSucceeded() {

    }
}
