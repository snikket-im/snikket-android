package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMagicCreateBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.InstallReferrerUtils;
import eu.siacs.conversations.xmpp.Jid;

import java.security.SecureRandom;

public class MagicCreateActivity extends XmppActivity implements TextWatcher {

    public static final String EXTRA_DOMAIN = "domain";
    public static final String EXTRA_PRE_AUTH = "pre_auth";
    public static final String EXTRA_USERNAME = "username";

    private ActivityMagicCreateBinding binding;
    private String domain;
    private String username;
    private String preAuth;

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {}

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        final Intent data = getIntent();
        this.domain = data == null ? null : data.getStringExtra(EXTRA_DOMAIN);
        this.preAuth = data == null ? null : data.getStringExtra(EXTRA_PRE_AUTH);
        this.username = data == null ? null : data.getStringExtra(EXTRA_USERNAME);
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_magic_create);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(this.binding.toolbar);
        configureActionBar(getSupportActionBar(), this.domain == null);
        if (username != null && domain != null) {
            binding.title.setText(R.string.your_server_invitation);
            binding.instructions.setText(getString(R.string.magic_create_text_fixed, domain));
            binding.username.setEnabled(false);
            binding.username.setText(this.username);
            updateFullJidInformation(this.username);
        } else if (domain != null) {
            binding.instructions.setText(getString(R.string.magic_create_text_on_x, domain));
        }
        binding.createAccount.setOnClickListener(
                v -> {
                    try {
                        final String username = binding.username.getText().toString();
                        final Jid jid;
                        final boolean fixedUsername;
                        if (this.domain != null && this.username != null) {
                            fixedUsername = true;
                            jid = Jid.ofLocalAndDomainEscaped(this.username, this.domain);
                        } else if (this.domain != null) {
                            fixedUsername = false;
                            jid = Jid.ofLocalAndDomainEscaped(username, this.domain);
                        } else {
                            fixedUsername = false;
                            jid = Jid.ofLocalAndDomainEscaped(username, Config.MAGIC_CREATE_DOMAIN);
                        }
                        if (!jid.getEscapedLocal().equals(jid.getLocal())
                                || (this.username == null && username.length() < 3)) {
                            binding.usernameLayout.setError(getString(R.string.invalid_username));
                            binding.username.requestFocus();
                        } else {
                            binding.usernameLayout.setError(null);
                            Account account = xmppConnectionService.findAccountByJid(jid);
                            if (account == null) {
                                account =
                                        new Account(
                                                jid,
                                                CryptoHelper.createPassword(new SecureRandom()));
                                account.setOption(Account.OPTION_REGISTER, true);
                                account.setOption(Account.OPTION_DISABLED, true);
                                account.setOption(Account.OPTION_MAGIC_CREATE, true);
                                account.setOption(Account.OPTION_FIXED_USERNAME, fixedUsername);
                                if (this.preAuth != null) {
                                    account.setKey(
                                            Account.KEY_PRE_AUTH_REGISTRATION_TOKEN, this.preAuth);
                                }
                                xmppConnectionService.createAccount(account);
                            }
                            Intent intent =
                                    new Intent(MagicCreateActivity.this, EditAccountActivity.class);
                            intent.putExtra("jid", account.getJid().asBareJid().toString());
                            intent.putExtra("init", true);
                            intent.setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            Toast.makeText(
                                            MagicCreateActivity.this,
                                            R.string.secure_password_generated,
                                            Toast.LENGTH_SHORT)
                                    .show();
                            StartConversationActivity.addInviteUri(intent, getIntent());
                            startActivity(intent);
                        }
                    } catch (final IllegalArgumentException e) {
                        binding.usernameLayout.setError(getString(R.string.invalid_username));
                        binding.username.requestFocus();
                    }
                });
        binding.username.addTextChangedListener(this);
    }

    @Override
    public void onDestroy() {
        InstallReferrerUtils.markInstallReferrerExecuted(this);
        super.onDestroy();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(final Editable s) {
        updateFullJidInformation(s.toString());
    }

    private void updateFullJidInformation(final String username) {
        if (username.trim().isEmpty()) {
            binding.fullJid.setVisibility(View.INVISIBLE);
        } else {
            try {
                binding.fullJid.setVisibility(View.VISIBLE);
                final Jid jid;
                if (this.domain == null) {
                    jid = Jid.ofLocalAndDomainEscaped(username, Config.MAGIC_CREATE_DOMAIN);
                } else {
                    jid = Jid.ofLocalAndDomainEscaped(username, this.domain);
                }
                binding.fullJid.setText(
                        getString(R.string.your_full_jid_will_be, jid.toEscapedString()));
                binding.usernameLayout.setError(null);
            } catch (final IllegalArgumentException e) {
                binding.fullJid.setVisibility(View.INVISIBLE);
            }
        }
    }
}
